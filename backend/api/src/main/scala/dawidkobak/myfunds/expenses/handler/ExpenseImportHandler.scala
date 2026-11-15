package dawidkobak.myfunds.expenses.handler

import java.time.LocalDate
import java.util.Base64

import cats.data.NonEmptyList
import cats.syntax.all.*
import dawidkobak.myfunds.domain.DatabaseAccess
import dawidkobak.myfunds.expenses.{ExpenseAutomaticClassification, ExpenseImportParser, ExpenseImportRow}
import doobie.*
import doobie.free.connection
import doobie.implicits.*
import doobie.implicits.javatimedrivernative.*
import io.circe.Json
import zio.*
import zio.interop.catz.*

final case class ExpenseCommitRow(
  selected: Boolean,
  folderId: Option[Int],
  folderPath: Option[String],
  paymentSourceId: Option[Int],
  paymentSource: Option[String],
  paymentSourceType: Option[String],
  categoryId: Option[Int],
  category: Option[String],
  categoryColor: Option[String],
  categoryIcon: Option[String],
  tagIds: List[Int],
  tags: List[String],
  name: String,
  note: Option[String],
  amount: BigDecimal,
  currency: String,
  expenseMonth: LocalDate,
  expenseDate: Option[LocalDate],
  entryType: String,
  countingStatus: String,
  fingerprint: String
)

object ExpenseImportHandler {
  def preview(payload: Json, ownerName: String): RIO[DatabaseAccess, Json] =
    for {
      databaseAccess <- ZIO.service[DatabaseAccess]
      fileName         <- ZIO.fromEither(payload.hcursor.get[String]("fileName"))
      contentBase64    <- ZIO.fromEither(payload.hcursor.get[String]("contentBase64"))
      content          <- ZIO.attempt(Base64.getDecoder.decode(contentBase64))
      preview          <- ZIO.attempt(ExpenseImportParser.parseFile(fileName, content))
      ownerId          <- sql"SELECT mf_user.id FROM mf_user WHERE mf_user.username = $ownerName".query[Int].unique.transact(databaseAccess.transactor)
      alreadyImported <- sql"""
        SELECT EXISTS (
          SELECT 1
          FROM mf_expense_import_batch
          WHERE mf_expense_import_batch.owner_id = $ownerId
            AND mf_expense_import_batch.format = ${preview.format}
            AND mf_expense_import_batch.checksum = ${preview.checksum}
            AND mf_expense_import_batch.status = 'committed'
        )
      """.query[Boolean].unique.transact(databaseAccess.transactor)
      existingFingerprints <- NonEmptyList.fromList(preview.rows.map(_.fingerprint).distinct) match {
        case None => ZIO.succeed(List.empty[String])
        case Some(fingerprints) =>
          (fr"SELECT mf_expense_item.source_fingerprint FROM mf_expense_item" ++
            Fragments.whereAnd(fr"mf_expense_item.owner_id = $ownerId", Fragments.in(fr"mf_expense_item.source_fingerprint", fingerprints)))
            .query[String]
            .to[List]
            .transact(databaseAccess.transactor)
      }
      paymentSources <- sql"""
        SELECT mf_expense_payment_source.id, mf_expense_payment_source.name
        FROM mf_expense_payment_source
        WHERE mf_expense_payment_source.owner_id = $ownerId AND mf_expense_payment_source.is_active = true
      """.query[(Int, String)].to[List].transact(databaseAccess.transactor)
      categories <- sql"""
        SELECT mf_expense_category.id, mf_expense_category.name
        FROM mf_expense_category
        WHERE mf_expense_category.owner_id = $ownerId AND mf_expense_category.is_active = true
      """.query[(Int, String)].to[List].transact(databaseAccess.transactor)
      tags <- sql"""
        SELECT mf_expense_tag.id, mf_expense_tag.name
        FROM mf_expense_tag
        WHERE mf_expense_tag.owner_id = $ownerId AND mf_expense_tag.is_active = true
      """.query[(Int, String)].to[List].transact(databaseAccess.transactor)
      duplicateFingerprints = existingFingerprints.toSet
      classifiedRows = preview.rows.map { row =>
        val resolvedPaymentSource = row.paymentSource.flatMap { paymentSourceName =>
          paymentSources.find(_._2.equalsIgnoreCase(paymentSourceName))
        }
        val automaticClassification = ExpenseAutomaticClassification.classify(
          row.name,
          row.note,
          row.amount,
          row.expenseMonth,
          resolvedPaymentSource.map(_._2).orElse(row.paymentSource)
        )
        val selectedCategoryName = row.category.orElse(automaticClassification.categoryName)
        val resolvedCategory = selectedCategoryName.flatMap { categoryName =>
          categories.find(_._2.equalsIgnoreCase(categoryName))
        }
        val selectedTagNames = (row.tags ++ automaticClassification.tagNames).foldLeft(List.empty[String]) { (currentTagNames, tagName) =>
          if (currentTagNames.exists(_.equalsIgnoreCase(tagName))) currentTagNames else currentTagNames :+ tagName
        }
        val selectedNote = automaticClassification.description match {
          case Some(description) if !row.note.exists(_.split("; ").exists(_.trim.equalsIgnoreCase(description))) =>
            Some((description :: row.note.toList).mkString("; "))
          case _ => row.note
        }
        val resolvedTagIds = selectedTagNames.flatMap { tagName =>
          tags.find(_._2.equalsIgnoreCase(tagName)).map(_._1)
        }
        (
          row.copy(category = selectedCategoryName, tags = selectedTagNames, note = selectedNote),
          resolvedPaymentSource.map(_._1),
          resolvedCategory.map(_._1),
          resolvedTagIds
        )
      }
      rowsJson = classifiedRows.map { case (row, resolvedPaymentSourceId, resolvedCategoryId, resolvedTagIds) =>
        val duplicate = alreadyImported || duplicateFingerprints.contains(row.fingerprint)
        importRowJson(row).deepMerge(
          Json.obj(
            "paymentSourceId" -> resolvedPaymentSourceId.fold(Json.Null)(Json.fromInt),
            "categoryId"      -> resolvedCategoryId.fold(Json.Null)(Json.fromInt),
            "tagIds"          -> Json.fromValues(resolvedTagIds.map(Json.fromInt)),
            "duplicate"       -> Json.fromBoolean(duplicate),
            "selected"        -> Json.fromBoolean(!duplicate)
          )
        )
      }
      totals = preview.rows.groupBy(_.currency).toList.sortBy(_._1).map { case (currency, rows) =>
        Json.obj("currency" -> Json.fromString(currency), "amount" -> Json.fromBigDecimal(rows.map(_.amount).sum))
      }
      convertedJson = Json.obj(
        "format"       -> Json.fromString("my-funds-expenses-v1"),
        "sourceFormat" -> Json.fromString(preview.format),
        "sourceFile"   -> Json.fromString(preview.fileName),
        "items"        -> Json.fromValues(classifiedRows.map(value => importRowJson(value._1)))
      )
    } yield Json.obj(
      "format"         -> Json.fromString(preview.format),
      "fileName"       -> Json.fromString(preview.fileName),
      "checksum"       -> Json.fromString(preview.checksum),
      "rows"           -> Json.fromValues(rowsJson),
      "warnings"       -> Json.fromValues(preview.warnings.map(Json.fromString)),
      "duplicates"     -> Json.fromInt(if (alreadyImported) preview.rows.size else existingFingerprints.size),
      "missingSources" -> Json.fromInt(preview.rows.count(_.paymentSource.isEmpty)),
      "totals"         -> Json.fromValues(totals),
      "convertedJson"  -> convertedJson
    )

  def commit(payload: Json, ownerName: String): RIO[DatabaseAccess, Json] =
    for {
      databaseAccess <- ZIO.service[DatabaseAccess]
      format           <- ZIO.fromEither(payload.hcursor.get[String]("format"))
      fileName         <- ZIO.fromEither(payload.hcursor.get[String]("fileName"))
      checksum         <- ZIO.fromEither(payload.hcursor.get[String]("checksum"))
      defaultFolderId        = payload.hcursor.get[Int]("folderId").toOption
      defaultPaymentSourceId = payload.hcursor.get[Int]("paymentSourceId").toOption
      rowPayloads <- ZIO.fromEither(payload.hcursor.get[List[Json]]("rows"))
      rows <- ZIO.foreach(rowPayloads) { rowPayload =>
        for {
          name         <- ZIO.fromEither(rowPayload.hcursor.get[String]("name"))
          amount       <- ZIO.fromEither(rowPayload.hcursor.get[BigDecimal]("amount"))
          expenseMonth <- ZIO.fromEither(rowPayload.hcursor.get[String]("month")).map(LocalDate.parse)
          fingerprint  <- ZIO.fromEither(rowPayload.hcursor.get[String]("fingerprint"))
          countingStatus = rowPayload.hcursor.get[String]("countingStatus").getOrElse("included")
          _ <- ZIO
            .fail(new IllegalArgumentException("Nieprawidłowy status uwzględniania importowanego wydatku"))
            .unless(
              Set("included", "excluded_internal_transfer", "excluded_business", "excluded_investment", "excluded_cash_transfer", "excluded_reimbursed", "excluded_duplicate").contains(countingStatus)
            )
        } yield ExpenseCommitRow(
          selected = rowPayload.hcursor.get[Boolean]("selected").getOrElse(true),
          folderId = rowPayload.hcursor.get[Int]("folderId").toOption,
          folderPath = rowPayload.hcursor.get[String]("folder").toOption.filter(_.trim.nonEmpty),
          paymentSourceId = rowPayload.hcursor.get[Int]("paymentSourceId").toOption,
          paymentSource = rowPayload.hcursor.get[String]("paymentSource").toOption.filter(_.trim.nonEmpty),
          paymentSourceType = rowPayload.hcursor.get[String]("paymentSourceType").toOption,
          categoryId = rowPayload.hcursor.get[Int]("categoryId").toOption,
          category = rowPayload.hcursor.get[String]("category").toOption.filter(_.trim.nonEmpty),
          categoryColor = rowPayload.hcursor.get[String]("categoryColor").toOption,
          categoryIcon = rowPayload.hcursor.get[String]("categoryIcon").toOption,
          tagIds = rowPayload.hcursor.get[List[Int]]("tagIds").getOrElse(List.empty),
          tags = rowPayload.hcursor.get[List[String]]("tags").getOrElse(List.empty),
          name = name.trim,
          note = rowPayload.hcursor.get[String]("note").toOption.filter(_.trim.nonEmpty),
          amount = amount,
          currency = rowPayload.hcursor.get[String]("currency").getOrElse("PLN").toUpperCase,
          expenseMonth = expenseMonth,
          expenseDate = rowPayload.hcursor.get[String]("date").toOption.filter(_.nonEmpty).map(LocalDate.parse),
          entryType = rowPayload.hcursor.get[String]("type").getOrElse(if (amount < 0) "refund" else "expense"),
          countingStatus = countingStatus,
          fingerprint = fingerprint
        )
      }
      selectedRows = rows.filter(_.selected)
      commitResult <- (for {
        ownerId <- sql"SELECT mf_user.id FROM mf_user WHERE mf_user.username = $ownerName".query[Int].unique
        _       <- sql"LOCK TABLE mf_expense_import_batch IN SHARE ROW EXCLUSIVE MODE".update.run
        alreadyImported <- sql"""
          SELECT EXISTS (
            SELECT 1
            FROM mf_expense_import_batch
            WHERE mf_expense_import_batch.owner_id = $ownerId
              AND mf_expense_import_batch.format = $format
              AND mf_expense_import_batch.checksum = $checksum
              AND mf_expense_import_batch.status = 'committed'
          )
        """.query[Boolean].unique
        _ <-
          if (alreadyImported) {
            connection.raiseError[Unit](new IllegalArgumentException(s"Plik $fileName został już zaimportowany"))
          } else {
            ().pure[ConnectionIO]
          }
        importBatchId <- sql"""
          INSERT INTO mf_expense_import_batch (
            owner_id, format, original_filename, checksum, status,
            total_rows, imported_rows, skipped_rows, created_at
          ) VALUES ($ownerId, $format, $fileName, $checksum, 'previewed', ${selectedRows.size}, 0, 0, NOW())
          RETURNING id
        """.query[Int].unique
        importedRows <- selectedRows.traverse { row =>
          for {
            selectedFolderId <- row.folderId match {
              case Some(folderId) =>
                sql"""SELECT mf_expense_folder.id FROM mf_expense_folder
                  WHERE mf_expense_folder.id = $folderId AND mf_expense_folder.owner_id = $ownerId""".query[Int].unique
              case None =>
                row.folderPath match {
                  case Some(folderPath) =>
                    val folderNames = folderPath.split("/", -1).toList.map(_.trim).filter(_.nonEmpty)
                    folderNames match {
                      case rootFolderName :: Nil =>
                        for {
                          _ <- sql"""INSERT INTO mf_expense_folder (owner_id, parent_id, name, depth, sort_order, created_at, updated_at)
                          VALUES ($ownerId, NULL, $rootFolderName, 0, 0, NOW(), NOW()) ON CONFLICT DO NOTHING""".update.run
                          folderId <- sql"""SELECT mf_expense_folder.id FROM mf_expense_folder
                          WHERE mf_expense_folder.owner_id = $ownerId AND mf_expense_folder.parent_id IS NULL
                            AND lower(mf_expense_folder.name) = lower($rootFolderName)""".query[Int].unique
                        } yield folderId
                      case rootFolderName :: childFolderName :: Nil =>
                        for {
                          _ <- sql"""INSERT INTO mf_expense_folder (owner_id, parent_id, name, depth, sort_order, created_at, updated_at)
                          VALUES ($ownerId, NULL, $rootFolderName, 0, 0, NOW(), NOW()) ON CONFLICT DO NOTHING""".update.run
                          rootFolderId <- sql"""SELECT mf_expense_folder.id FROM mf_expense_folder
                          WHERE mf_expense_folder.owner_id = $ownerId AND mf_expense_folder.parent_id IS NULL
                            AND lower(mf_expense_folder.name) = lower($rootFolderName)""".query[Int].unique
                          _ <- sql"""INSERT INTO mf_expense_folder (owner_id, parent_id, name, depth, sort_order, created_at, updated_at)
                          VALUES ($ownerId, $rootFolderId, $childFolderName, 1, 0, NOW(), NOW()) ON CONFLICT DO NOTHING""".update.run
                          childFolderId <- sql"""SELECT mf_expense_folder.id FROM mf_expense_folder
                          WHERE mf_expense_folder.owner_id = $ownerId AND mf_expense_folder.parent_id = $rootFolderId
                            AND lower(mf_expense_folder.name) = lower($childFolderName)""".query[Int].unique
                        } yield childFolderId
                      case _ => connection.raiseError[Int](new IllegalArgumentException(s"Ścieżka folderu ${row.folderPath.get} ma więcej niż dwa poziomy"))
                    }
                  case None =>
                    defaultFolderId match {
                      case Some(folderId) =>
                        sql"""SELECT mf_expense_folder.id FROM mf_expense_folder
                      WHERE mf_expense_folder.id = $folderId AND mf_expense_folder.owner_id = $ownerId""".query[Int].unique
                      case None => connection.raiseError[Int](new IllegalArgumentException(s"Wiersz ${row.name} nie ma folderu"))
                    }
                }
            }
            selectedPaymentSourceId <- row.paymentSourceId match {
              case Some(paymentSourceId) =>
                sql"""SELECT mf_expense_payment_source.id FROM mf_expense_payment_source
                  WHERE mf_expense_payment_source.id = $paymentSourceId AND mf_expense_payment_source.owner_id = $ownerId""".query[Int].unique
              case None =>
                row.paymentSource match {
                  case Some(paymentSourceName) =>
                    val paymentSourceType = row.paymentSourceType.filter(value => Set("bank", "cash", "manual", "legacy").contains(value)).getOrElse("manual")
                    for {
                      _ <- sql"""INSERT INTO mf_expense_payment_source (owner_id, name, type, is_active, created_at, updated_at)
                      VALUES ($ownerId, $paymentSourceName, $paymentSourceType, true, NOW(), NOW()) ON CONFLICT DO NOTHING""".update.run
                      paymentSourceId <- sql"""SELECT mf_expense_payment_source.id FROM mf_expense_payment_source
                      WHERE mf_expense_payment_source.owner_id = $ownerId
                        AND lower(mf_expense_payment_source.name) = lower($paymentSourceName)""".query[Int].unique
                    } yield paymentSourceId
                  case None =>
                    defaultPaymentSourceId match {
                      case Some(paymentSourceId) =>
                        sql"""SELECT mf_expense_payment_source.id FROM mf_expense_payment_source
                      WHERE mf_expense_payment_source.id = $paymentSourceId AND mf_expense_payment_source.owner_id = $ownerId""".query[Int].unique
                      case None => connection.raiseError[Int](new IllegalArgumentException(s"Wiersz ${row.name} nie ma źródła płatności"))
                    }
                }
            }
            selectedPaymentSourceName <- sql"""SELECT mf_expense_payment_source.name
              FROM mf_expense_payment_source
              WHERE mf_expense_payment_source.id = $selectedPaymentSourceId
                AND mf_expense_payment_source.owner_id = $ownerId""".query[String].unique
            automaticClassification = ExpenseAutomaticClassification.classify(
              row.name,
              row.note,
              row.amount,
              row.expenseMonth,
              Some(selectedPaymentSourceName)
            )
            selectedNote = automaticClassification.description match {
              case Some(description) if !row.note.exists(_.split("; ").exists(_.trim.equalsIgnoreCase(description))) =>
                Some((description :: row.note.toList).mkString("; "))
              case _ => row.note
            }
            selectedCategoryId <- row.categoryId match {
              case Some(categoryId) =>
                sql"""SELECT mf_expense_category.id FROM mf_expense_category
                  WHERE mf_expense_category.id = $categoryId AND mf_expense_category.owner_id = $ownerId""".query[Int].map(Option.apply).unique
              case None =>
                row.category.orElse(automaticClassification.categoryName) match {
                  case Some(categoryName) =>
                    val categoryColor = row.categoryColor.filter(_.matches("^#[0-9A-Fa-f]{6}$")).getOrElse("#78716C")
                    val categoryIcon  = row.categoryIcon.getOrElse("ellipsis")
                    for {
                      _ <- sql"""INSERT INTO mf_expense_category (owner_id, name, color, icon, is_active, created_at, updated_at)
                        VALUES ($ownerId, $categoryName, $categoryColor, $categoryIcon, true, NOW(), NOW()) ON CONFLICT DO NOTHING""".update.run
                      categoryId <- sql"""SELECT mf_expense_category.id FROM mf_expense_category
                        WHERE mf_expense_category.owner_id = $ownerId
                          AND lower(mf_expense_category.name) = lower($categoryName)""".query[Int].option
                    } yield categoryId
                  case None => Option.empty[Int].pure[ConnectionIO]
                }
            }
            currencyId <- sql"SELECT mf_currency.id FROM mf_currency WHERE mf_currency.code = ${row.currency}".query[Int].unique
            itemId <- sql"""
              INSERT INTO mf_expense_item (
                owner_id, folder_id, payment_source_id, category_id, currency_id, import_batch_id,
                name, note, amount, expense_month, expense_date, entry_type, counting_status, source_fingerprint, created_at, updated_at
              ) VALUES (
                $ownerId, $selectedFolderId, $selectedPaymentSourceId, $selectedCategoryId, $currencyId, $importBatchId,
                ${row.name}, $selectedNote, ${row.amount}, ${row.expenseMonth}, ${row.expenseDate}, ${row.entryType}, ${row.countingStatus}, ${row.fingerprint}, NOW(), NOW()
              )
              ON CONFLICT DO NOTHING
              RETURNING id
            """.query[Int].option
            _ <- itemId.toList.traverse { insertedItemId =>
              val selectedTagNames = (row.tags ++ automaticClassification.tagNames).foldLeft(List.empty[String]) { (currentTagNames, tagName) =>
                if (currentTagNames.exists(_.equalsIgnoreCase(tagName))) currentTagNames else currentTagNames :+ tagName
              }
              for {
                _ <- selectedTagNames.map(_.trim).filter(_.nonEmpty).traverse { tagName =>
                  sql"""INSERT INTO mf_expense_tag (owner_id, name, is_active, created_at, updated_at)
                    VALUES ($ownerId, $tagName, true, NOW(), NOW()) ON CONFLICT DO NOTHING""".update.run
                }
                importedTagIds <- (row.tagIds
                  .traverse { tagId =>
                    sql"SELECT mf_expense_tag.id FROM mf_expense_tag WHERE mf_expense_tag.id = $tagId AND mf_expense_tag.owner_id = $ownerId".query[Int].option
                  })
                  .map(_.flatten)
                importedNamedTagIds <- selectedTagNames.map(_.trim).filter(_.nonEmpty).traverse { tagName =>
                  sql"""SELECT mf_expense_tag.id FROM mf_expense_tag
                    WHERE mf_expense_tag.owner_id = $ownerId AND lower(mf_expense_tag.name) = lower($tagName)""".query[Int].unique
                }
                _ <- (importedTagIds ++ importedNamedTagIds).distinct.traverse { tagId =>
                  sql"INSERT INTO mf_expense_item_tag (expense_item_id, tag_id) VALUES ($insertedItemId, $tagId) ON CONFLICT DO NOTHING".update.run
                }
                _ <- sql"""
                  INSERT INTO mf_expense_item_tag (expense_item_id, tag_id)
                  SELECT $insertedItemId, mf_expense_tag.id
                  FROM mf_expense_folder
                  JOIN mf_expense_tag
                    ON mf_expense_tag.owner_id = mf_expense_folder.owner_id
                    AND lower(mf_expense_tag.name) = lower(mf_expense_folder.name)
                    AND mf_expense_tag.is_active = true
                  WHERE mf_expense_folder.id = $selectedFolderId
                    AND mf_expense_folder.owner_id = $ownerId
                  ON CONFLICT DO NOTHING
                """.update.run
              } yield ()
            }
          } yield itemId.nonEmpty
        }
        importedCount = importedRows.count(identity)
        skippedCount  = selectedRows.size - importedCount
        _ <- sql"""UPDATE mf_expense_import_batch
          SET status = 'committed', imported_rows = $importedCount, skipped_rows = $skippedCount, committed_at = NOW()
          WHERE mf_expense_import_batch.id = $importBatchId""".update.run
      } yield (importBatchId, importedCount, skippedCount)).transact(databaseAccess.transactor)
    } yield Json.obj(
      "batchId"  -> Json.fromInt(commitResult._1),
      "imported" -> Json.fromInt(commitResult._2),
      "skipped"  -> Json.fromInt(commitResult._3)
    )

  private def importRowJson(row: ExpenseImportRow): Json =
    Json.obj(
      "rowNumber"         -> Json.fromInt(row.rowNumber),
      "folder"            -> row.folderPath.fold(Json.Null)(Json.fromString),
      "paymentSource"     -> row.paymentSource.fold(Json.Null)(Json.fromString),
      "paymentSourceType" -> row.paymentSourceType.fold(Json.Null)(Json.fromString),
      "category"          -> row.category.fold(Json.Null)(Json.fromString),
      "categoryColor"     -> row.categoryColor.fold(Json.Null)(Json.fromString),
      "categoryIcon"      -> row.categoryIcon.fold(Json.Null)(Json.fromString),
      "tags"              -> Json.fromValues(row.tags.map(Json.fromString)),
      "name"              -> Json.fromString(row.name),
      "note"              -> row.note.fold(Json.Null)(Json.fromString),
      "amount"            -> Json.fromBigDecimal(row.amount),
      "currency"          -> Json.fromString(row.currency),
      "month"             -> Json.fromString(row.expenseMonth.toString),
      "date"              -> row.expenseDate.fold(Json.Null)(value => Json.fromString(value.toString)),
      "type"              -> Json.fromString(row.entryType),
      "countingStatus"    -> Json.fromString(row.countingStatus),
      "fingerprint"       -> Json.fromString(row.fingerprint),
      "warnings"          -> Json.fromValues(row.warnings.map(Json.fromString))
    )
}
