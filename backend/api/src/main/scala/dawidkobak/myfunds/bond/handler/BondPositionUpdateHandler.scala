package dawidkobak.myfunds.bond.handler

import dawidkobak.myfunds.domain.DatabaseAccess
import doobie.implicits.*
import io.circe.Json
import zio.*
import zio.interop.catz.*

object BondPositionUpdateHandler {
  def apply(payload: Json): RIO[DatabaseAccess, Json] =
    for {
      databaseAccess   <- ZIO.service[DatabaseAccess]
      bondPositionId     <- ZIO.fromEither(payload.hcursor.get[Int]("id"))
      walletId           <- ZIO.fromEither(payload.hcursor.downField("wallet").get[Int]("id")).option
      bondIssueId        <- ZIO.fromEither(payload.hcursor.downField("bondIssue").get[Int]("id")).option
      availableUnits     <- ZIO.fromEither(payload.hcursor.get[BigDecimal]("availableUnits")).option
      blockedUnits       <- ZIO.fromEither(payload.hcursor.get[BigDecimal]("blockedUnits")).option
      purchaseDate       <- ZIO.fromEither(payload.hcursor.get[String]("purchaseDate")).option
      maturityDate       <- ZIO.fromEither(payload.hcursor.get[String]("maturityDate")).option
      purchaseCost       <- ZIO.fromEither(payload.hcursor.get[BigDecimal]("purchaseCost")).option
      sourceFilePath     <- ZIO.fromEither(payload.hcursor.get[String]("sourceFilePath")).option
      sourceValueDate    <- ZIO.fromEither(payload.hcursor.get[String]("sourceValueDate")).option
      sourceCurrentValue <- ZIO.fromEither(payload.hcursor.get[BigDecimal]("sourceCurrentValue")).option
      bondPosition <-
        sql"""
              WITH updated_bond_position AS (
                UPDATE mf_bond_position SET
                  wallet_id = ${if (walletId.isDefined) walletId.get else fr"""wallet_id"""},
                  bond_issue_id = ${if (bondIssueId.isDefined) bondIssueId.get else fr"""bond_issue_id"""},
                  available_units = ${if (availableUnits.isDefined) availableUnits.get else fr"""available_units"""},
                  blocked_units = ${if (blockedUnits.isDefined) blockedUnits.get else fr"""blocked_units"""},
                  purchase_date = ${if (purchaseDate.isDefined) purchaseDate.get else fr"""purchase_date"""}::date,
                  maturity_date = ${if (maturityDate.isDefined) maturityDate.get else fr"""maturity_date"""}::date,
                  purchase_cost = ${if (purchaseCost.isDefined) purchaseCost.get else fr"""purchase_cost"""},
                  source_file_path = ${if (sourceFilePath.isDefined) sourceFilePath.get else fr"""source_file_path"""},
                  source_value_date = ${if (sourceValueDate.isDefined) sourceValueDate.get else fr"""source_value_date"""}::date,
                  source_current_value = ${if (sourceCurrentValue.isDefined) sourceCurrentValue.get else fr"""source_current_value"""},
                  updated_at = NOW()
                WHERE id = $bondPositionId
                RETURNING id, wallet_id, bond_issue_id, available_units, blocked_units, purchase_date, maturity_date, purchase_cost, source_file_path, source_value_date, source_current_value
              )
              SELECT
                updated_bond_position.id,
                updated_bond_position.wallet_id,
                mf_wallet.name,
                updated_bond_position.bond_issue_id,
                mf_bond_issue.symbol,
                updated_bond_position.available_units,
                updated_bond_position.blocked_units,
                updated_bond_position.purchase_date,
                updated_bond_position.maturity_date,
                updated_bond_position.purchase_cost,
                updated_bond_position.source_file_path,
                updated_bond_position.source_value_date,
                updated_bond_position.source_current_value
              FROM updated_bond_position
              JOIN mf_wallet ON updated_bond_position.wallet_id = mf_wallet.id
              JOIN mf_bond_issue ON updated_bond_position.bond_issue_id = mf_bond_issue.id
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
