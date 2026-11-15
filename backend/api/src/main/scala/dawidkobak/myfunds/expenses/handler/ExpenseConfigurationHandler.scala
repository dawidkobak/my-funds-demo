package dawidkobak.myfunds.expenses.handler

import dawidkobak.myfunds.domain.DatabaseAccess
import doobie.implicits.*
import io.circe.Json
import zio.*
import zio.interop.catz.*

object ExpenseConfigurationHandler {
  def createFolder(payload: Json, ownerName: String): RIO[DatabaseAccess, Json] =
    for {
      databaseAccess <- ZIO.service[DatabaseAccess]
      name             <- ZIO.fromEither(payload.hcursor.get[String]("name")).map(_.trim).filterOrFail(_.nonEmpty)(new IllegalArgumentException("Nazwa folderu jest wymagana"))
      parentId  = payload.hcursor.get[Int]("parentId").toOption
      sortOrder = payload.hcursor.get[Int]("sortOrder").getOrElse(0)
      ownerId <- sql"SELECT mf_user.id FROM mf_user WHERE mf_user.username = $ownerName".query[Int].unique.transact(databaseAccess.transactor)
      folderId <- parentId match {
        case None =>
          sql"""
            INSERT INTO mf_expense_folder (owner_id, parent_id, name, depth, sort_order, created_at, updated_at)
            VALUES ($ownerId, NULL, $name, 0, $sortOrder, NOW(), NOW())
            RETURNING id
          """.query[Int].unique.transact(databaseAccess.transactor)
        case Some(selectedParentId) =>
          sql"""
            INSERT INTO mf_expense_folder (owner_id, parent_id, name, depth, sort_order, created_at, updated_at)
            SELECT $ownerId, mf_expense_folder.id, $name, 1, $sortOrder, NOW(), NOW()
            FROM mf_expense_folder
            WHERE mf_expense_folder.id = $selectedParentId
              AND mf_expense_folder.owner_id = $ownerId
              AND mf_expense_folder.parent_id IS NULL
            RETURNING id
          """.query[Int].unique.transact(databaseAccess.transactor)
      }
    } yield Json.obj("id" -> Json.fromInt(folderId))

  def updateFolder(folderId: Int, payload: Json, ownerName: String): RIO[DatabaseAccess, Json] =
    for {
      databaseAccess <- ZIO.service[DatabaseAccess]
      name             <- ZIO.fromEither(payload.hcursor.get[String]("name")).map(_.trim).filterOrFail(_.nonEmpty)(new IllegalArgumentException("Nazwa folderu jest wymagana"))
      sortOrder = payload.hcursor.get[Int]("sortOrder").getOrElse(0)
      updatedRows <- sql"""
        UPDATE mf_expense_folder SET name = $name, sort_order = $sortOrder, updated_at = NOW()
        WHERE mf_expense_folder.id = $folderId
          AND mf_expense_folder.owner_id = (SELECT mf_user.id FROM mf_user WHERE mf_user.username = $ownerName)
      """.update.run.transact(databaseAccess.transactor)
    } yield Json.obj("updated" -> Json.fromInt(updatedRows))

  def deleteFolder(folderId: Int, ownerName: String): RIO[DatabaseAccess, Json] =
    for {
      databaseAccess <- ZIO.service[DatabaseAccess]
      ownerId          <- sql"SELECT mf_user.id FROM mf_user WHERE mf_user.username = $ownerName".query[Int].unique.transact(databaseAccess.transactor)
      itemCount <- sql"""
        SELECT COUNT(*) FROM mf_expense_item
        WHERE mf_expense_item.folder_id = $folderId AND mf_expense_item.owner_id = $ownerId
      """.query[Long].unique.transact(databaseAccess.transactor)
      childCount <- sql"""
        SELECT COUNT(*) FROM mf_expense_folder
        WHERE mf_expense_folder.parent_id = $folderId AND mf_expense_folder.owner_id = $ownerId
      """.query[Long].unique.transact(databaseAccess.transactor)
      deletedRows <- ZIO.when(itemCount == 0 && childCount == 0) {
        sql"DELETE FROM mf_expense_folder WHERE mf_expense_folder.id = $folderId AND mf_expense_folder.owner_id = $ownerId".update.run.transact(databaseAccess.transactor)
      }
    } yield Json.obj(
      "deleted"    -> Json.fromInt(deletedRows.getOrElse(0)),
      "blocked"    -> Json.fromBoolean(itemCount > 0 || childCount > 0),
      "itemCount"  -> Json.fromLong(itemCount),
      "childCount" -> Json.fromLong(childCount)
    )

  def saveCategory(payload: Json, ownerName: String): RIO[DatabaseAccess, Json] =
    for {
      databaseAccess <- ZIO.service[DatabaseAccess]
      categoryId = payload.hcursor.get[Int]("id").toOption
      name  <- ZIO.fromEither(payload.hcursor.get[String]("name")).map(_.trim).filterOrFail(_.nonEmpty)(new IllegalArgumentException("Nazwa kategorii jest wymagana"))
      color <- ZIO.fromEither(payload.hcursor.get[String]("color"))
      icon  <- ZIO.fromEither(payload.hcursor.get[String]("icon"))
      isActive = payload.hcursor.get[Boolean]("isActive").getOrElse(true)
      ownerId <- sql"SELECT mf_user.id FROM mf_user WHERE mf_user.username = $ownerName".query[Int].unique.transact(databaseAccess.transactor)
      savedCategoryId <- categoryId match {
        case None =>
          sql"""
            INSERT INTO mf_expense_category (owner_id, name, color, icon, is_active, created_at, updated_at)
            VALUES ($ownerId, $name, $color, $icon, $isActive, NOW(), NOW()) RETURNING id
          """.query[Int].unique.transact(databaseAccess.transactor)
        case Some(selectedCategoryId) =>
          sql"""
            UPDATE mf_expense_category SET name = $name, color = $color, icon = $icon,
              is_active = $isActive, updated_at = NOW()
            WHERE mf_expense_category.id = $selectedCategoryId AND mf_expense_category.owner_id = $ownerId
            RETURNING id
          """.query[Int].unique.transact(databaseAccess.transactor)
      }
    } yield Json.obj("id" -> Json.fromInt(savedCategoryId))

  def saveTag(payload: Json, ownerName: String): RIO[DatabaseAccess, Json] =
    for {
      databaseAccess <- ZIO.service[DatabaseAccess]
      tagId = payload.hcursor.get[Int]("id").toOption
      name <- ZIO.fromEither(payload.hcursor.get[String]("name")).map(_.trim).filterOrFail(_.nonEmpty)(new IllegalArgumentException("Nazwa tagu jest wymagana"))
      isActive = payload.hcursor.get[Boolean]("isActive").getOrElse(true)
      ownerId <- sql"SELECT mf_user.id FROM mf_user WHERE mf_user.username = $ownerName".query[Int].unique.transact(databaseAccess.transactor)
      savedTagId <- tagId match {
        case None =>
          sql"""
            INSERT INTO mf_expense_tag (owner_id, name, is_active, created_at, updated_at)
            VALUES ($ownerId, $name, $isActive, NOW(), NOW()) RETURNING id
          """.query[Int].unique.transact(databaseAccess.transactor)
        case Some(selectedTagId) =>
          sql"""
            UPDATE mf_expense_tag SET name = $name, is_active = $isActive, updated_at = NOW()
            WHERE mf_expense_tag.id = $selectedTagId AND mf_expense_tag.owner_id = $ownerId RETURNING id
          """.query[Int].unique.transact(databaseAccess.transactor)
      }
    } yield Json.obj("id" -> Json.fromInt(savedTagId))

  def savePaymentSource(payload: Json, ownerName: String): RIO[DatabaseAccess, Json] =
    for {
      databaseAccess <- ZIO.service[DatabaseAccess]
      paymentSourceId = payload.hcursor.get[Int]("id").toOption
      name <- ZIO.fromEither(payload.hcursor.get[String]("name")).map(_.trim).filterOrFail(_.nonEmpty)(new IllegalArgumentException("Nazwa źródła jest wymagana"))
      paymentSourceType = payload.hcursor.get[String]("type").getOrElse("manual")
      isActive          = payload.hcursor.get[Boolean]("isActive").getOrElse(true)
      ownerId <- sql"SELECT mf_user.id FROM mf_user WHERE mf_user.username = $ownerName".query[Int].unique.transact(databaseAccess.transactor)
      savedPaymentSourceId <- paymentSourceId match {
        case None =>
          sql"""
            INSERT INTO mf_expense_payment_source (owner_id, name, type, is_active, created_at, updated_at)
            VALUES ($ownerId, $name, $paymentSourceType, $isActive, NOW(), NOW()) RETURNING id
          """.query[Int].unique.transact(databaseAccess.transactor)
        case Some(selectedPaymentSourceId) =>
          sql"""
            UPDATE mf_expense_payment_source SET name = $name, type = $paymentSourceType,
              is_active = $isActive, updated_at = NOW()
            WHERE mf_expense_payment_source.id = $selectedPaymentSourceId
              AND mf_expense_payment_source.owner_id = $ownerId RETURNING id
          """.query[Int].unique.transact(databaseAccess.transactor)
      }
    } yield Json.obj("id" -> Json.fromInt(savedPaymentSourceId))

  def deactivate(configurationType: String, configurationId: Int, ownerName: String): RIO[DatabaseAccess, Json] =
    for {
      databaseAccess <- ZIO.service[DatabaseAccess]
      updatedRows <- configurationType match {
        case "category" =>
          sql"""UPDATE mf_expense_category SET is_active = false, updated_at = NOW()
            WHERE mf_expense_category.id = $configurationId
              AND mf_expense_category.owner_id = (SELECT mf_user.id FROM mf_user WHERE mf_user.username = $ownerName)""".update.run.transact(databaseAccess.transactor)
        case "tag" =>
          sql"""UPDATE mf_expense_tag SET is_active = false, updated_at = NOW()
            WHERE mf_expense_tag.id = $configurationId
              AND mf_expense_tag.owner_id = (SELECT mf_user.id FROM mf_user WHERE mf_user.username = $ownerName)""".update.run.transact(databaseAccess.transactor)
        case "payment-source" =>
          sql"""UPDATE mf_expense_payment_source SET is_active = false, updated_at = NOW()
            WHERE mf_expense_payment_source.id = $configurationId
              AND mf_expense_payment_source.owner_id = (SELECT mf_user.id FROM mf_user WHERE mf_user.username = $ownerName)""".update.run.transact(databaseAccess.transactor)
        case _ => ZIO.fail(new IllegalArgumentException("Nieznany typ konfiguracji"))
      }
    } yield Json.obj("updated" -> Json.fromInt(updatedRows))
}
