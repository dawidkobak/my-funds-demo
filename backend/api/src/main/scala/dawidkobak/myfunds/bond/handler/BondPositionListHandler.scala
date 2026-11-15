package dawidkobak.myfunds.bond.handler

import java.time.LocalDate

import dawidkobak.myfunds.domain.DatabaseAccess
import doobie.implicits.*
import io.circe.Json
import zio.*
import zio.interop.catz.*

object BondPositionListHandler {
  def apply(walletId: Option[Int], valuationDate: Option[String]): RIO[DatabaseAccess, Json] =
    for {
      databaseAccess      <- ZIO.service[DatabaseAccess]
      resolvedValuationDate <- ZIO.attempt(valuationDate.getOrElse(LocalDate.now.toString))
      bondPositions <-
        sql"""
              SELECT
                mf_bond_position.id,
                mf_wallet.id,
                mf_wallet.name,
                mf_wallet.wallet_type,
                mf_bond_issue.id,
                mf_bond_issue.symbol,
                mf_bond_issue.bond_type,
                mf_bond_issue.nominal_value,
                mf_bond_issue.sale_start_date,
                mf_bond_issue.sale_end_date,
                mf_bond_issue.maturity_years,
                mf_bond_issue.issue_letter_url,
                mf_bond_issue.interest_table_url,
                mf_bond_position.available_units,
                mf_bond_position.blocked_units,
                mf_bond_position.purchase_date,
                mf_bond_position.maturity_date,
                mf_bond_position.purchase_cost,
                mf_bond_position.source_file_path,
                mf_bond_position.source_value_date,
                mf_bond_position.source_current_value,
                COALESCE(mf_bond_interest_value.accrued_interest, 0),
                mf_bond_issue.nominal_value + COALESCE(mf_bond_interest_value.accrued_interest, 0),
                (mf_bond_position.available_units + mf_bond_position.blocked_units) * (mf_bond_issue.nominal_value + COALESCE(mf_bond_interest_value.accrued_interest, 0))
              FROM mf_bond_position
              JOIN mf_wallet ON mf_bond_position.wallet_id = mf_wallet.id
              JOIN mf_bond_issue ON mf_bond_position.bond_issue_id = mf_bond_issue.id
              LEFT JOIN mf_bond_interest_value ON mf_bond_interest_value.bond_issue_id = mf_bond_issue.id
                AND mf_bond_interest_value.base_valuation_date = ($resolvedValuationDate::date - (mf_bond_position.purchase_date - mf_bond_issue.sale_start_date))
              WHERE ${walletId.fold(fr"""true""")(walletId => fr"mf_bond_position.wallet_id = $walletId")}
                AND mf_bond_position.purchase_date <= $resolvedValuationDate::date
              ORDER BY mf_bond_position.maturity_date, mf_bond_issue.symbol, mf_bond_position.purchase_date
           """
          .query[
            (
              Int,
              Int,
              String,
              String,
              Int,
              String,
              String,
              BigDecimal,
              String,
              String,
              Int,
              Option[String],
              Option[String],
              BigDecimal,
              BigDecimal,
              String,
              String,
              BigDecimal,
              Option[String],
              Option[String],
              Option[BigDecimal],
              BigDecimal,
              BigDecimal,
              BigDecimal
            )
          ]
          .to[List]
          .transact(databaseAccess.transactor)
    } yield Json.fromValues(
      bondPositions.map {
        case (
              bondPositionId,
              positionWalletId,
              walletName,
              walletType,
              bondIssueId,
              symbol,
              bondType,
              nominalValue,
              saleStartDate,
              saleEndDate,
              maturityYears,
              issueLetterUrl,
              interestTableUrl,
              availableUnits,
              blockedUnits,
              purchaseDate,
              maturityDate,
              purchaseCost,
              sourceFilePath,
              sourceValueDate,
              sourceCurrentValue,
              accruedInterest,
              currentUnitValue,
              currentTotalValue
            ) =>
          Json.obj(
            "id" -> Json.fromInt(bondPositionId),
            "wallet" -> Json.obj(
              "id"         -> Json.fromInt(positionWalletId),
              "name"       -> Json.fromString(walletName),
              "walletType" -> Json.fromString(walletType)
            ),
            "bondIssue" -> Json.obj(
              "id"               -> Json.fromInt(bondIssueId),
              "symbol"           -> Json.fromString(symbol),
              "bondType"         -> Json.fromString(bondType),
              "nominalValue"     -> Json.fromBigDecimal(nominalValue),
              "saleStartDate"    -> Json.fromString(saleStartDate),
              "saleEndDate"      -> Json.fromString(saleEndDate),
              "maturityYears"    -> Json.fromInt(maturityYears),
              "issueLetterUrl"   -> issueLetterUrl.fold(Json.Null)(Json.fromString),
              "interestTableUrl" -> interestTableUrl.fold(Json.Null)(Json.fromString)
            ),
            "availableUnits"     -> Json.fromBigDecimal(availableUnits),
            "blockedUnits"       -> Json.fromBigDecimal(blockedUnits),
            "totalUnits"         -> Json.fromBigDecimal(availableUnits + blockedUnits),
            "purchaseDate"       -> Json.fromString(purchaseDate),
            "maturityDate"       -> Json.fromString(maturityDate),
            "purchaseCost"       -> Json.fromBigDecimal(purchaseCost),
            "sourceFilePath"     -> sourceFilePath.fold(Json.Null)(Json.fromString),
            "sourceValueDate"    -> sourceValueDate.fold(Json.Null)(Json.fromString),
            "sourceCurrentValue" -> sourceCurrentValue.fold(Json.Null)(Json.fromBigDecimal),
            "accruedInterest"    -> Json.fromBigDecimal(accruedInterest),
            "currentUnitValue"   -> Json.fromBigDecimal(currentUnitValue),
            "currentTotalValue"  -> Json.fromBigDecimal(currentTotalValue)
          )
      }
    )
}
