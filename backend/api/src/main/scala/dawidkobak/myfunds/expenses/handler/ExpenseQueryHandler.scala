package dawidkobak.myfunds.expenses.handler

import java.nio.charset.StandardCharsets
import java.time.LocalDate
import java.util.Base64

import dawidkobak.myfunds.domain.DatabaseAccess
import dawidkobak.myfunds.expenses.ExpenseSearchFilter
import doobie.implicits.*
import doobie.implicits.javatimedrivernative.*
import io.circe.Json
import zio.*
import zio.interop.catz.*

object ExpenseQueryHandler {
  def bootstrap(ownerName: String): RIO[DatabaseAccess, Json] =
    for {
      databaseAccess <- ZIO.service[DatabaseAccess]
      ownerId <- sql"SELECT mf_user.id FROM mf_user WHERE mf_user.username = $ownerName"
        .query[Int]
        .unique
        .transact(databaseAccess.transactor)
      folders <- sql"""
        SELECT mf_expense_folder.id, mf_expense_folder.parent_id, mf_expense_folder.name,
               mf_expense_folder.depth, mf_expense_folder.sort_order
        FROM mf_expense_folder
        WHERE mf_expense_folder.owner_id = $ownerId
        ORDER BY mf_expense_folder.depth, mf_expense_folder.sort_order, mf_expense_folder.name
      """.query[(Int, Option[Int], String, Int, Int)].to[List].transact(databaseAccess.transactor)
      categories <- sql"""
        SELECT mf_expense_category.id, mf_expense_category.name, mf_expense_category.color,
               mf_expense_category.icon, mf_expense_category.is_active
        FROM mf_expense_category
        WHERE mf_expense_category.owner_id = $ownerId
        ORDER BY mf_expense_category.is_active DESC, mf_expense_category.name
      """.query[(Int, String, String, String, Boolean)].to[List].transact(databaseAccess.transactor)
      tags <- sql"""
        SELECT mf_expense_tag.id, mf_expense_tag.name, mf_expense_tag.is_active
        FROM mf_expense_tag
        WHERE mf_expense_tag.owner_id = $ownerId
        ORDER BY mf_expense_tag.is_active DESC, mf_expense_tag.name
      """.query[(Int, String, Boolean)].to[List].transact(databaseAccess.transactor)
      paymentSources <- sql"""
        SELECT mf_expense_payment_source.id, mf_expense_payment_source.name,
               mf_expense_payment_source.type, mf_expense_payment_source.is_active
        FROM mf_expense_payment_source
        WHERE mf_expense_payment_source.owner_id = $ownerId
        ORDER BY mf_expense_payment_source.is_active DESC, mf_expense_payment_source.name
      """.query[(Int, String, String, Boolean)].to[List].transact(databaseAccess.transactor)
      currencies <- sql"SELECT mf_currency.id, mf_currency.code, mf_currency.name FROM mf_currency ORDER BY mf_currency.code"
        .query[(Int, String, String)]
        .to[List]
        .transact(databaseAccess.transactor)
      migrationReport <- sql"""
        SELECT mf_expense_import_batch.total_rows, mf_expense_import_batch.imported_rows,
               mf_expense_import_batch.skipped_rows, mf_expense_import_batch.committed_at
        FROM mf_expense_import_batch
        WHERE mf_expense_import_batch.owner_id = $ownerId
          AND mf_expense_import_batch.format = 'legacy'
        ORDER BY mf_expense_import_batch.id DESC
        LIMIT 1
      """.query[(Int, Int, Int, Option[java.time.LocalDateTime])].option.transact(databaseAccess.transactor)
    } yield Json.obj(
      "folders" -> Json.fromValues(folders.map { case (folderId, parentId, name, depth, sortOrder) =>
        Json.obj(
          "id"        -> Json.fromInt(folderId),
          "parentId"  -> parentId.fold(Json.Null)(Json.fromInt),
          "name"      -> Json.fromString(name),
          "depth"     -> Json.fromInt(depth),
          "sortOrder" -> Json.fromInt(sortOrder)
        )
      }),
      "categories" -> Json.fromValues(categories.map { case (categoryId, name, color, icon, isActive) =>
        Json.obj(
          "id"       -> Json.fromInt(categoryId),
          "name"     -> Json.fromString(name),
          "color"    -> Json.fromString(color),
          "icon"     -> Json.fromString(icon),
          "isActive" -> Json.fromBoolean(isActive)
        )
      }),
      "tags" -> Json.fromValues(tags.map { case (tagId, name, isActive) =>
        Json.obj("id" -> Json.fromInt(tagId), "name" -> Json.fromString(name), "isActive" -> Json.fromBoolean(isActive))
      }),
      "paymentSources" -> Json.fromValues(paymentSources.map { case (paymentSourceId, name, paymentSourceType, isActive) =>
        Json.obj(
          "id"       -> Json.fromInt(paymentSourceId),
          "name"     -> Json.fromString(name),
          "type"     -> Json.fromString(paymentSourceType),
          "isActive" -> Json.fromBoolean(isActive)
        )
      }),
      "currencies" -> Json.fromValues(currencies.map { case (currencyId, code, name) =>
        Json.obj("id" -> Json.fromInt(currencyId), "code" -> Json.fromString(code), "name" -> Json.fromString(name))
      }),
      "legacyMigration" -> migrationReport.fold(Json.Null) { case (totalRows, importedRows, skippedRows, committedAt) =>
        Json.obj(
          "totalRows"    -> Json.fromInt(totalRows),
          "importedRows" -> Json.fromInt(importedRows),
          "skippedRows"  -> Json.fromInt(skippedRows),
          "committedAt"  -> committedAt.fold(Json.Null)(value => Json.fromString(value.toString))
        )
      }
    )

  def search(payload: Json, ownerName: String): RIO[DatabaseAccess, Json] =
    for {
      databaseAccess <- ZIO.service[DatabaseAccess]
      searchFilter     <- ZIO.attempt(ExpenseSearchFilter.fromJson(payload))
      ownerId <- sql"SELECT mf_user.id FROM mf_user WHERE mf_user.username = $ownerName"
        .query[Int]
        .unique
        .transact(databaseAccess.transactor)
      offset = searchFilter.page * searchFilter.pageSize
      items <- (fr"""
        SELECT mf_expense_item.id, mf_expense_item.folder_id, mf_expense_folder.name,
               mf_expense_item.payment_source_id, mf_expense_payment_source.name,
               mf_expense_item.category_id, mf_expense_category.name, mf_expense_category.color,
               mf_expense_category.icon, mf_expense_item.currency_id, mf_currency.code,
               mf_expense_item.name, mf_expense_item.note, mf_expense_item.amount,
               mf_expense_item.expense_month, mf_expense_item.expense_date, mf_expense_item.entry_type,
               mf_expense_item.counting_status,
               COALESCE((
                 SELECT string_agg(mf_expense_tag.id::text || ':' || replace(mf_expense_tag.name, '|', ''), '|' ORDER BY mf_expense_tag.name)
                 FROM mf_expense_item_tag
                 JOIN mf_expense_tag ON mf_expense_tag.id = mf_expense_item_tag.tag_id
                 WHERE mf_expense_item_tag.expense_item_id = mf_expense_item.id
               ), '')
        FROM mf_expense_item
        JOIN mf_expense_folder ON mf_expense_folder.id = mf_expense_item.folder_id
        JOIN mf_expense_payment_source ON mf_expense_payment_source.id = mf_expense_item.payment_source_id
        LEFT JOIN mf_expense_category ON mf_expense_category.id = mf_expense_item.category_id
        JOIN mf_currency ON mf_currency.id = mf_expense_item.currency_id
      """ ++ searchFilter.where(ownerId) ++ searchFilter.orderBy ++ fr"LIMIT ${searchFilter.pageSize} OFFSET $offset")
        .query[
          (
            Int,
            Int,
            String,
            Int,
            String,
            Option[Int],
            Option[String],
            Option[String],
            Option[String],
            Int,
            String,
            String,
            Option[String],
            BigDecimal,
            LocalDate,
            Option[LocalDate],
            String,
            String,
            String
          )
        ]
        .to[List]
        .transact(databaseAccess.transactor)
      totalCount <- (fr"SELECT COUNT(*) FROM mf_expense_item" ++ searchFilter.where(ownerId))
        .query[Long]
        .unique
        .transact(databaseAccess.transactor)
      totals <- (fr"""
        SELECT mf_currency.code, COALESCE(SUM(mf_expense_item.amount), 0)
        FROM mf_expense_item
        JOIN mf_currency ON mf_currency.id = mf_expense_item.currency_id
      """ ++ searchFilter.where(ownerId, forceIncluded = true) ++ fr"GROUP BY mf_currency.code ORDER BY mf_currency.code")
        .query[(String, BigDecimal)]
        .to[List]
        .transact(databaseAccess.transactor)
    } yield Json.obj(
      "items"      -> Json.fromValues(items.map(itemJson)),
      "totalCount" -> Json.fromLong(totalCount),
      "page"       -> Json.fromInt(searchFilter.page),
      "pageSize"   -> Json.fromInt(searchFilter.pageSize),
      "totals" -> Json.fromValues(totals.map { case (currency, amount) =>
        Json.obj("currency" -> Json.fromString(currency), "amount" -> Json.fromBigDecimal(amount))
      })
    )

  def reports(payload: Json, ownerName: String): RIO[DatabaseAccess, Json] =
    for {
      databaseAccess <- ZIO.service[DatabaseAccess]
      searchFilter     <- ZIO.attempt(ExpenseSearchFilter.fromJson(payload))
      ownerId <- sql"SELECT mf_user.id FROM mf_user WHERE mf_user.username = $ownerName"
        .query[Int]
        .unique
        .transact(databaseAccess.transactor)
      periodGranularity = payload.hcursor.get[String]("periodGranularity").toOption.filter(_ == "day").getOrElse("month")
      periodExpression =
        if (periodGranularity == "day") fr"COALESCE(mf_expense_item.expense_date, mf_expense_item.expense_month)"
        else fr"mf_expense_item.expense_month"
      monthly <- (fr"SELECT" ++ periodExpression ++ fr""", mf_currency.code, SUM(mf_expense_item.amount)
        FROM mf_expense_item
        JOIN mf_currency ON mf_currency.id = mf_expense_item.currency_id
      """ ++ searchFilter.where(ownerId, forceIncluded = true) ++ fr"GROUP BY" ++ periodExpression ++ fr", mf_currency.code ORDER BY" ++ periodExpression ++ fr", mf_currency.code")
        .query[(LocalDate, String, BigDecimal)]
        .to[List]
        .transact(databaseAccess.transactor)
      totals <- (fr"""
        SELECT mf_currency.code, COALESCE(SUM(mf_expense_item.amount), 0)
        FROM mf_expense_item
        JOIN mf_currency ON mf_currency.id = mf_expense_item.currency_id
      """ ++ searchFilter.where(ownerId, forceIncluded = true) ++ fr"GROUP BY mf_currency.code ORDER BY mf_currency.code")
        .query[(String, BigDecimal)]
        .to[List]
        .transact(databaseAccess.transactor)
      categories <- (fr"""
        SELECT COALESCE(mf_expense_category.name, 'Bez kategorii'),
               COALESCE(mf_expense_category.color, '#78716C'),
               COALESCE(mf_expense_category.icon, 'ellipsis'), mf_currency.code, SUM(mf_expense_item.amount)
        FROM mf_expense_item
        LEFT JOIN mf_expense_category ON mf_expense_category.id = mf_expense_item.category_id
        JOIN mf_currency ON mf_currency.id = mf_expense_item.currency_id
      """ ++ searchFilter.where(ownerId, forceIncluded = true) ++ fr"""
        GROUP BY mf_expense_category.name, mf_expense_category.color, mf_expense_category.icon, mf_currency.code
        ORDER BY mf_currency.code, SUM(mf_expense_item.amount) DESC
      """).query[(String, String, String, String, BigDecimal)].to[List].transact(databaseAccess.transactor)
      paymentSources <- (fr"""
        SELECT mf_expense_payment_source.name, mf_currency.code, SUM(mf_expense_item.amount)
        FROM mf_expense_item
        JOIN mf_expense_payment_source ON mf_expense_payment_source.id = mf_expense_item.payment_source_id
        JOIN mf_currency ON mf_currency.id = mf_expense_item.currency_id
      """ ++ searchFilter.where(ownerId, forceIncluded = true) ++ fr"""
        GROUP BY mf_expense_payment_source.name, mf_currency.code
        ORDER BY mf_currency.code, SUM(mf_expense_item.amount) DESC
      """).query[(String, String, BigDecimal)].to[List].transact(databaseAccess.transactor)
      tags <- (fr"""
        SELECT mf_expense_tag.name, mf_currency.code, SUM(mf_expense_item.amount)
        FROM mf_expense_item
        JOIN mf_expense_item_tag ON mf_expense_item_tag.expense_item_id = mf_expense_item.id
        JOIN mf_expense_tag ON mf_expense_tag.id = mf_expense_item_tag.tag_id
        JOIN mf_currency ON mf_currency.id = mf_expense_item.currency_id
      """ ++ searchFilter.where(ownerId, forceIncluded = true) ++ fr"""
        GROUP BY mf_expense_tag.name, mf_currency.code
        ORDER BY mf_currency.code, SUM(mf_expense_item.amount) DESC
      """).query[(String, String, BigDecimal)].to[List].transact(databaseAccess.transactor)
    } yield Json.obj(
      "periodGranularity" -> Json.fromString(periodGranularity),
      "monthly" -> Json.fromValues(monthly.map { case (month, currency, amount) =>
        Json.obj("month" -> Json.fromString(month.toString), "currency" -> Json.fromString(currency), "amount" -> Json.fromBigDecimal(amount))
      }),
      "totals" -> Json.fromValues(totals.map { case (currency, amount) =>
        Json.obj("currency" -> Json.fromString(currency), "amount" -> Json.fromBigDecimal(amount))
      }),
      "categories" -> Json.fromValues(categories.map { case (name, color, icon, currency, amount) =>
        Json.obj(
          "name"     -> Json.fromString(name),
          "color"    -> Json.fromString(color),
          "icon"     -> Json.fromString(icon),
          "currency" -> Json.fromString(currency),
          "amount"   -> Json.fromBigDecimal(amount)
        )
      }),
      "paymentSources" -> Json.fromValues(paymentSources.map { case (name, currency, amount) =>
        Json.obj("name" -> Json.fromString(name), "currency" -> Json.fromString(currency), "amount" -> Json.fromBigDecimal(amount))
      }),
      "tags" -> Json.fromValues(tags.map { case (name, currency, amount) =>
        Json.obj("name" -> Json.fromString(name), "currency" -> Json.fromString(currency), "amount" -> Json.fromBigDecimal(amount))
      })
    )

  def exportData(payload: Json, ownerName: String): RIO[DatabaseAccess, Json] =
    for {
      databaseAccess <- ZIO.service[DatabaseAccess]
      searchFilter     <- ZIO.attempt(ExpenseSearchFilter.fromJson(payload))
      exportFormat     <- ZIO.succeed(payload.hcursor.get[String]("format").getOrElse("json").toLowerCase)
      ownerId <- sql"SELECT mf_user.id FROM mf_user WHERE mf_user.username = $ownerName"
        .query[Int]
        .unique
        .transact(databaseAccess.transactor)
      folders <- sql"""SELECT mf_expense_folder.id, mf_expense_folder.name
        FROM mf_expense_folder WHERE mf_expense_folder.owner_id = $ownerId"""
        .query[(Int, String)]
        .to[List]
        .transact(databaseAccess.transactor)
      items <- (fr"""
        SELECT mf_expense_item.id,
               mf_expense_folder.parent_id, mf_expense_folder.name,
               mf_expense_payment_source.name, mf_expense_category.name,
               COALESCE((SELECT string_agg(mf_expense_tag.name, ';' ORDER BY mf_expense_tag.name)
                         FROM mf_expense_item_tag JOIN mf_expense_tag ON mf_expense_tag.id = mf_expense_item_tag.tag_id
                         WHERE mf_expense_item_tag.expense_item_id = mf_expense_item.id), ''),
               mf_expense_item.name, mf_expense_item.note, mf_expense_item.amount, mf_currency.code,
               mf_expense_item.expense_month, mf_expense_item.expense_date, mf_expense_item.entry_type,
               mf_expense_item.counting_status, mf_expense_item.source_fingerprint
        FROM mf_expense_item
        JOIN mf_expense_folder ON mf_expense_folder.id = mf_expense_item.folder_id
        JOIN mf_expense_payment_source ON mf_expense_payment_source.id = mf_expense_item.payment_source_id
        LEFT JOIN mf_expense_category ON mf_expense_category.id = mf_expense_item.category_id
        JOIN mf_currency ON mf_currency.id = mf_expense_item.currency_id
      """ ++ searchFilter.where(ownerId) ++ searchFilter.orderBy)
        .query[(Int, Option[Int], String, String, Option[String], String, String, Option[String], BigDecimal, String, LocalDate, Option[LocalDate], String, String, Option[String])]
        .to[List]
        .transact(databaseAccess.transactor)
      configuration <- bootstrap(ownerName)
      content <- ZIO.attempt {
        if (exportFormat == "csv") {
          val header = "folder,source,category,tags,name,note,amount,currency,month,date,type,countingStatus,fingerprint"
          val lines = items.map { case (_, parentFolderId, folderName, source, category, tags, name, note, amount, currency, month, date, entryType, countingStatus, fingerprint) =>
            val folderPath = parentFolderId.flatMap(parentId => folders.find(_._1 == parentId).map(_._2)).map(parentName => s"$parentName/$folderName").getOrElse(folderName)
            List(
              folderPath,
              source,
              category.getOrElse(""),
              tags,
              name,
              note.getOrElse(""),
              amount.bigDecimal.toPlainString,
              currency,
              month.toString,
              date.map(_.toString).getOrElse(""),
              entryType,
              countingStatus,
              fingerprint.getOrElse("")
            )
              .map(csvCell)
              .mkString(",")
          }
          (s"my-funds-expenses-${LocalDate.now()}.csv", "text/csv;charset=utf-8", (header :: lines).mkString("\n"))
        } else {
          val exportedItems = Json.fromValues(items.map {
            case (_, parentFolderId, folderName, source, category, tags, name, note, amount, currency, month, date, entryType, countingStatus, fingerprint) =>
              val folderPath = parentFolderId.flatMap(parentId => folders.find(_._1 == parentId).map(_._2)).map(parentName => s"$parentName/$folderName").getOrElse(folderName)
              Json.obj(
                "folder"         -> Json.fromString(folderPath),
                "source"         -> Json.fromString(source),
                "category"       -> category.fold(Json.Null)(Json.fromString),
                "tags"           -> Json.fromValues(tags.split(";").toList.filter(_.nonEmpty).map(Json.fromString)),
                "name"           -> Json.fromString(name),
                "note"           -> note.fold(Json.Null)(Json.fromString),
                "amount"         -> Json.fromBigDecimal(amount),
                "currency"       -> Json.fromString(currency),
                "month"          -> Json.fromString(month.toString),
                "date"           -> date.fold(Json.Null)(value => Json.fromString(value.toString)),
                "type"           -> Json.fromString(entryType),
                "countingStatus" -> Json.fromString(countingStatus),
                "fingerprint"    -> fingerprint.fold(Json.Null)(Json.fromString)
              )
          })
          val exportJson = Json.obj(
            "format"     -> Json.fromString("my-funds-expenses-v1"),
            "exportedAt" -> Json.fromString(java.time.Instant.now().toString),
            "folders"    -> configuration.hcursor.downField("folders").focus.getOrElse(Json.arr()),
            "sources"    -> configuration.hcursor.downField("paymentSources").focus.getOrElse(Json.arr()),
            "categories" -> configuration.hcursor.downField("categories").focus.getOrElse(Json.arr()),
            "tags"       -> configuration.hcursor.downField("tags").focus.getOrElse(Json.arr()),
            "items"      -> exportedItems
          )
          (s"my-funds-expenses-${LocalDate.now()}.json", "application/json", exportJson.spaces2)
        }
      }
    } yield Json.obj(
      "fileName"      -> Json.fromString(content._1),
      "mediaType"     -> Json.fromString(content._2),
      "contentBase64" -> Json.fromString(Base64.getEncoder.encodeToString(content._3.getBytes(StandardCharsets.UTF_8)))
    )

  private def itemJson(
    item: (
      Int,
      Int,
      String,
      Int,
      String,
      Option[Int],
      Option[String],
      Option[String],
      Option[String],
      Int,
      String,
      String,
      Option[String],
      BigDecimal,
      LocalDate,
      Option[LocalDate],
      String,
      String,
      String
    )
  ): Json = {
    val tags = item._19.split("\\|").toList.filter(_.nonEmpty).flatMap { serializedTag =>
      serializedTag.split(":", 2).toList match {
        case tagId :: tagName :: Nil => tagId.toIntOption.map(id => Json.obj("id" -> Json.fromInt(id), "name" -> Json.fromString(tagName)))
        case _                       => None
      }
    }
    Json.obj(
      "id"            -> Json.fromInt(item._1),
      "folder"        -> Json.obj("id" -> Json.fromInt(item._2), "name" -> Json.fromString(item._3)),
      "paymentSource" -> Json.obj("id" -> Json.fromInt(item._4), "name" -> Json.fromString(item._5)),
      "category" -> item._6.fold(Json.Null) { categoryId =>
        Json.obj(
          "id"    -> Json.fromInt(categoryId),
          "name"  -> item._7.fold(Json.Null)(Json.fromString),
          "color" -> item._8.fold(Json.Null)(Json.fromString),
          "icon"  -> item._9.fold(Json.Null)(Json.fromString)
        )
      },
      "currency"       -> Json.obj("id" -> Json.fromInt(item._10), "code" -> Json.fromString(item._11)),
      "name"           -> Json.fromString(item._12),
      "note"           -> item._13.fold(Json.Null)(Json.fromString),
      "amount"         -> Json.fromBigDecimal(item._14),
      "month"          -> Json.fromString(item._15.toString),
      "date"           -> item._16.fold(Json.Null)(value => Json.fromString(value.toString)),
      "type"           -> Json.fromString(item._17),
      "countingStatus" -> Json.fromString(item._18),
      "tags"           -> Json.fromValues(tags)
    )
  }

  private def csvCell(value: String): String = s"\"${value.replace("\"", "\"\"")}\""
}
