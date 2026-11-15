package dawidkobak.myfunds.expenses.handler

import java.time.LocalDate

import cats.syntax.all.*
import dawidkobak.myfunds.domain.DatabaseAccess
import doobie.*
import doobie.free.connection
import doobie.implicits.*
import doobie.implicits.javatimedrivernative.*
import io.circe.Json
import zio.*
import zio.interop.catz.*

object ExpenseItemHandler {
  def create(payload: Json, ownerName: String): RIO[DatabaseAccess, Json] =
    for {
      databaseAccess <- ZIO.service[DatabaseAccess]
      folderId         <- ZIO.fromEither(payload.hcursor.get[Int]("folderId"))
      paymentSourceId  <- ZIO.fromEither(payload.hcursor.get[Int]("paymentSourceId"))
      categoryId = payload.hcursor.get[Int]("categoryId").toOption
      currencyId <- ZIO.fromEither(payload.hcursor.get[Int]("currencyId"))
      name       <- ZIO.fromEither(payload.hcursor.get[String]("name")).map(_.trim).filterOrFail(_.nonEmpty)(new IllegalArgumentException("Nazwa wydatku jest wymagana"))
      note = payload.hcursor.get[String]("note").toOption.map(_.trim).filter(_.nonEmpty)
      amount       <- ZIO.fromEither(payload.hcursor.get[BigDecimal]("amount"))
      expenseMonth <- ZIO.fromEither(payload.hcursor.get[String]("month")).map(LocalDate.parse)
      _            <- ZIO.fail(new IllegalArgumentException("Miesiąc musi wskazywać pierwszy dzień miesiąca")).unless(expenseMonth.getDayOfMonth == 1)
      expenseDate    = payload.hcursor.get[String]("date").toOption.filter(_.nonEmpty).map(LocalDate.parse)
      entryType      = payload.hcursor.get[String]("type").getOrElse(if (amount < 0) "refund" else "expense")
      countingStatus = payload.hcursor.get[String]("countingStatus").getOrElse("included")
      _ <- ZIO
        .fail(new IllegalArgumentException("Nieprawidłowy status uwzględniania wydatku"))
        .unless(
          Set("included", "excluded_internal_transfer", "excluded_business", "excluded_investment", "excluded_cash_transfer", "excluded_reimbursed", "excluded_duplicate").contains(countingStatus)
        )
      tagIds = payload.hcursor.get[List[Int]]("tagIds").getOrElse(List.empty).distinct
      createdItemId <- (for {
        ownerId <- sql"SELECT mf_user.id FROM mf_user WHERE mf_user.username = $ownerName".query[Int].unique
        itemIdOption <- sql"""
          INSERT INTO mf_expense_item (
            owner_id, folder_id, payment_source_id, category_id, currency_id,
            name, note, amount, expense_month, expense_date, entry_type, counting_status, created_at, updated_at
          )
          SELECT $ownerId, $folderId, $paymentSourceId, $categoryId, $currencyId,
                 $name, $note, $amount, $expenseMonth, $expenseDate, $entryType, $countingStatus, NOW(), NOW()
          WHERE EXISTS (SELECT 1 FROM mf_expense_folder WHERE mf_expense_folder.id = $folderId AND mf_expense_folder.owner_id = $ownerId)
            AND EXISTS (SELECT 1 FROM mf_expense_payment_source WHERE mf_expense_payment_source.id = $paymentSourceId AND mf_expense_payment_source.owner_id = $ownerId)
            AND EXISTS (SELECT 1 FROM mf_currency WHERE mf_currency.id = $currencyId)
            AND ($categoryId IS NULL OR EXISTS (SELECT 1 FROM mf_expense_category WHERE mf_expense_category.id = $categoryId AND mf_expense_category.owner_id = $ownerId))
          RETURNING id
        """.query[Int].option
        itemId <- itemIdOption.fold(connection.raiseError[Int](new IllegalArgumentException("Nieprawidłowe dane powiązane wydatku")))(connection.pure)
        _ <- tagIds.traverse { tagId =>
          sql"""
            INSERT INTO mf_expense_item_tag (expense_item_id, tag_id)
            SELECT $itemId, mf_expense_tag.id
            FROM mf_expense_tag
            WHERE mf_expense_tag.id = $tagId AND mf_expense_tag.owner_id = $ownerId
            ON CONFLICT DO NOTHING
          """.update.run
        }
        _ <- sql"""
          INSERT INTO mf_expense_item_tag (expense_item_id, tag_id)
          SELECT $itemId, mf_expense_tag.id
          FROM mf_expense_folder
          JOIN mf_expense_tag
            ON mf_expense_tag.owner_id = mf_expense_folder.owner_id
            AND lower(mf_expense_tag.name) = lower(mf_expense_folder.name)
            AND mf_expense_tag.is_active = true
          WHERE mf_expense_folder.id = $folderId
            AND mf_expense_folder.owner_id = $ownerId
          ON CONFLICT DO NOTHING
        """.update.run
      } yield itemId).transact(databaseAccess.transactor)
    } yield Json.obj("id" -> Json.fromInt(createdItemId))

  def update(itemId: Int, payload: Json, ownerName: String): RIO[DatabaseAccess, Json] =
    for {
      databaseAccess <- ZIO.service[DatabaseAccess]
      folderId         <- ZIO.fromEither(payload.hcursor.get[Int]("folderId"))
      paymentSourceId  <- ZIO.fromEither(payload.hcursor.get[Int]("paymentSourceId"))
      categoryId = payload.hcursor.get[Int]("categoryId").toOption
      currencyId <- ZIO.fromEither(payload.hcursor.get[Int]("currencyId"))
      name       <- ZIO.fromEither(payload.hcursor.get[String]("name")).map(_.trim).filterOrFail(_.nonEmpty)(new IllegalArgumentException("Nazwa wydatku jest wymagana"))
      note = payload.hcursor.get[String]("note").toOption.map(_.trim).filter(_.nonEmpty)
      amount       <- ZIO.fromEither(payload.hcursor.get[BigDecimal]("amount"))
      expenseMonth <- ZIO.fromEither(payload.hcursor.get[String]("month")).map(LocalDate.parse)
      _            <- ZIO.fail(new IllegalArgumentException("Miesiąc musi wskazywać pierwszy dzień miesiąca")).unless(expenseMonth.getDayOfMonth == 1)
      expenseDate    = payload.hcursor.get[String]("date").toOption.filter(_.nonEmpty).map(LocalDate.parse)
      entryType      = payload.hcursor.get[String]("type").getOrElse(if (amount < 0) "refund" else "expense")
      countingStatus = payload.hcursor.get[String]("countingStatus").getOrElse("included")
      _ <- ZIO
        .fail(new IllegalArgumentException("Nieprawidłowy status uwzględniania wydatku"))
        .unless(
          Set("included", "excluded_internal_transfer", "excluded_business", "excluded_investment", "excluded_cash_transfer", "excluded_reimbursed", "excluded_duplicate").contains(countingStatus)
        )
      tagIds = payload.hcursor.get[List[Int]]("tagIds").getOrElse(List.empty).distinct
      updatedRows <- (for {
        ownerId <- sql"SELECT mf_user.id FROM mf_user WHERE mf_user.username = $ownerName".query[Int].unique
        affectedRows <- sql"""
          UPDATE mf_expense_item
          SET folder_id = $folderId, payment_source_id = $paymentSourceId, category_id = $categoryId,
              currency_id = $currencyId, name = $name, note = $note, amount = $amount,
              expense_month = $expenseMonth, expense_date = $expenseDate, entry_type = $entryType,
              counting_status = $countingStatus, updated_at = NOW()
          WHERE mf_expense_item.id = $itemId AND mf_expense_item.owner_id = $ownerId
            AND EXISTS (SELECT 1 FROM mf_expense_folder WHERE mf_expense_folder.id = $folderId AND mf_expense_folder.owner_id = $ownerId)
            AND EXISTS (SELECT 1 FROM mf_expense_payment_source WHERE mf_expense_payment_source.id = $paymentSourceId AND mf_expense_payment_source.owner_id = $ownerId)
            AND EXISTS (SELECT 1 FROM mf_currency WHERE mf_currency.id = $currencyId)
            AND ($categoryId IS NULL OR EXISTS (SELECT 1 FROM mf_expense_category WHERE mf_expense_category.id = $categoryId AND mf_expense_category.owner_id = $ownerId))
        """.update.run
        _ <- connection.raiseError[Unit](new IllegalArgumentException("Nie znaleziono wydatku lub danych powiązanych")).whenA(affectedRows == 0)
        _ <- sql"DELETE FROM mf_expense_item_tag WHERE mf_expense_item_tag.expense_item_id = $itemId".update.run
        _ <- tagIds.traverse { tagId =>
          sql"""
            INSERT INTO mf_expense_item_tag (expense_item_id, tag_id)
            SELECT $itemId, mf_expense_tag.id FROM mf_expense_tag
            WHERE mf_expense_tag.id = $tagId AND mf_expense_tag.owner_id = $ownerId
            ON CONFLICT DO NOTHING
          """.update.run
        }
        _ <- sql"""
          INSERT INTO mf_expense_item_tag (expense_item_id, tag_id)
          SELECT $itemId, mf_expense_tag.id
          FROM mf_expense_folder
          JOIN mf_expense_tag
            ON mf_expense_tag.owner_id = mf_expense_folder.owner_id
            AND lower(mf_expense_tag.name) = lower(mf_expense_folder.name)
            AND mf_expense_tag.is_active = true
          WHERE mf_expense_folder.id = $folderId
            AND mf_expense_folder.owner_id = $ownerId
          ON CONFLICT DO NOTHING
        """.update.run
      } yield affectedRows).transact(databaseAccess.transactor)
    } yield Json.obj("updated" -> Json.fromInt(updatedRows))

  def delete(itemId: Int, ownerName: String): RIO[DatabaseAccess, Json] =
    for {
      databaseAccess <- ZIO.service[DatabaseAccess]
      deletedRows <- sql"""
        DELETE FROM mf_expense_item
        WHERE mf_expense_item.id = $itemId
          AND mf_expense_item.owner_id = (SELECT mf_user.id FROM mf_user WHERE mf_user.username = $ownerName)
      """.update.run.transact(databaseAccess.transactor)
    } yield Json.obj("deleted" -> Json.fromInt(deletedRows))
}
