package dawidkobak.myfunds.bond.handler

import dawidkobak.myfunds.domain.DatabaseAccess
import doobie.implicits.*
import io.circe.Json
import zio.*
import zio.interop.catz.*

object BondPositionCreateHandler {
  def apply(payload: Json): RIO[DatabaseAccess, Json] =
    for {
      databaseAccess   <- ZIO.service[DatabaseAccess]
      walletId           <- ZIO.fromEither(payload.hcursor.downField("wallet").get[Int]("id"))
      bondIssueId        <- ZIO.fromEither(payload.hcursor.downField("bondIssue").get[Int]("id"))
      availableUnits     <- ZIO.fromEither(payload.hcursor.get[BigDecimal]("availableUnits"))
      blockedUnits       <- ZIO.fromEither(payload.hcursor.get[BigDecimal]("blockedUnits"))
      purchaseDate       <- ZIO.fromEither(payload.hcursor.get[String]("purchaseDate"))
      maturityDate       <- ZIO.fromEither(payload.hcursor.get[String]("maturityDate"))
      purchaseCost       <- ZIO.fromEither(payload.hcursor.get[BigDecimal]("purchaseCost"))
      sourceFilePath     <- ZIO.fromEither(payload.hcursor.get[String]("sourceFilePath")).option
      sourceValueDate    <- ZIO.fromEither(payload.hcursor.get[String]("sourceValueDate")).option
      sourceCurrentValue <- ZIO.fromEither(payload.hcursor.get[BigDecimal]("sourceCurrentValue")).option
      bondPosition <-
        sql"""
              WITH inserted_bond_position AS (
                INSERT INTO mf_bond_position (
                  wallet_id,
                  bond_issue_id,
                  available_units,
                  blocked_units,
                  purchase_date,
                  maturity_date,
                  purchase_cost,
                  source_file_path,
                  source_value_date,
                  source_current_value,
                  created_at,
                  updated_at
                )
                SELECT
                  $walletId,
                  $bondIssueId,
                  $availableUnits,
                  $blockedUnits,
                  $purchaseDate::date,
                  $maturityDate::date,
                  $purchaseCost,
                  $sourceFilePath,
                  $sourceValueDate::date,
                  $sourceCurrentValue,
                  NOW(),
                  NOW()
                WHERE EXISTS (
                  SELECT 1 FROM mf_wallet WHERE mf_wallet.id = $walletId AND mf_wallet.wallet_type = 'BOND'
                )
                RETURNING id, wallet_id, bond_issue_id, available_units, blocked_units, purchase_date, maturity_date, purchase_cost, source_file_path, source_value_date, source_current_value
              )
              SELECT
                inserted_bond_position.id,
                inserted_bond_position.wallet_id,
                mf_wallet.name,
                inserted_bond_position.bond_issue_id,
                mf_bond_issue.symbol,
                inserted_bond_position.available_units,
                inserted_bond_position.blocked_units,
                inserted_bond_position.purchase_date,
                inserted_bond_position.maturity_date,
                inserted_bond_position.purchase_cost,
                inserted_bond_position.source_file_path,
                inserted_bond_position.source_value_date,
                inserted_bond_position.source_current_value
              FROM inserted_bond_position
              JOIN mf_wallet ON inserted_bond_position.wallet_id = mf_wallet.id
              JOIN mf_bond_issue ON inserted_bond_position.bond_issue_id = mf_bond_issue.id
           """
          .query[(Int, Int, String, Int, String, BigDecimal, BigDecimal, String, String, BigDecimal, Option[String], Option[String], Option[BigDecimal])]
          .unique
          .transact(databaseAccess.transactor)
    } yield Json.obj(
      "id" -> Json.fromInt(bondPosition._1),
      "wallet" -> Json.obj(
        "id"   -> Json.fromInt(bondPosition._2),
        "name" -> Json.fromString(bondPosition._3)
      ),
      "bondIssue" -> Json.obj(
        "id"     -> Json.fromInt(bondPosition._4),
        "symbol" -> Json.fromString(bondPosition._5)
      ),
      "availableUnits"     -> Json.fromBigDecimal(bondPosition._6),
      "blockedUnits"       -> Json.fromBigDecimal(bondPosition._7),
      "purchaseDate"       -> Json.fromString(bondPosition._8),
      "maturityDate"       -> Json.fromString(bondPosition._9),
      "purchaseCost"       -> Json.fromBigDecimal(bondPosition._10),
      "sourceFilePath"     -> bondPosition._11.fold(Json.Null)(Json.fromString),
      "sourceValueDate"    -> bondPosition._12.fold(Json.Null)(Json.fromString),
      "sourceCurrentValue" -> bondPosition._13.fold(Json.Null)(Json.fromBigDecimal)
    )
}
