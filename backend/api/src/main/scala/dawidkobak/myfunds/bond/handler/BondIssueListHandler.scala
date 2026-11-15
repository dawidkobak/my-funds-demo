package dawidkobak.myfunds.bond.handler

import dawidkobak.myfunds.domain.DatabaseAccess
import doobie.implicits.*
import io.circe.Json
import zio.*
import zio.interop.catz.*

object BondIssueListHandler {
  def apply(bondType: Option[String]): RIO[DatabaseAccess, Json] =
    for {
      databaseAccess <- ZIO.service[DatabaseAccess]
      bondIssues <-
        sql"""
              SELECT
                mf_bond_issue.id,
                mf_bond_issue.symbol,
                mf_bond_issue.bond_type,
                mf_bond_issue.nominal_value,
                mf_bond_issue.sale_start_date,
                mf_bond_issue.sale_end_date,
                mf_bond_issue.maturity_years,
                mf_bond_issue.first_year_interest_rate,
                mf_bond_issue.interest_margin,
                mf_bond_issue.capitalization_frequency,
                mf_bond_issue.interest_payment_type,
                mf_bond_issue.issue_letter_url,
                mf_bond_issue.interest_table_url
              FROM mf_bond_issue
              WHERE ${bondType.fold(fr"""true""")(bondType => fr"mf_bond_issue.bond_type = $bondType")}
              ORDER BY mf_bond_issue.symbol
           """
          .query[(Int, String, String, BigDecimal, String, String, Int, Option[BigDecimal], Option[BigDecimal], Option[String], Option[String], Option[String], Option[String])]
          .to[List]
          .transact(databaseAccess.transactor)
    } yield Json.fromValues(
      bondIssues.map {
        case (
              bondIssueId,
              symbol,
              issueBondType,
              nominalValue,
              saleStartDate,
              saleEndDate,
              maturityYears,
              firstYearInterestRate,
              interestMargin,
              capitalizationFrequency,
              interestPaymentType,
              issueLetterUrl,
              interestTableUrl
            ) =>
          Json.obj(
            "id"                      -> Json.fromInt(bondIssueId),
            "symbol"                  -> Json.fromString(symbol),
            "bondType"                -> Json.fromString(issueBondType),
            "nominalValue"            -> Json.fromBigDecimal(nominalValue),
            "saleStartDate"           -> Json.fromString(saleStartDate),
            "saleEndDate"             -> Json.fromString(saleEndDate),
            "maturityYears"           -> Json.fromInt(maturityYears),
            "firstYearInterestRate"   -> firstYearInterestRate.fold(Json.Null)(Json.fromBigDecimal),
            "interestMargin"          -> interestMargin.fold(Json.Null)(Json.fromBigDecimal),
            "capitalizationFrequency" -> capitalizationFrequency.fold(Json.Null)(Json.fromString),
            "interestPaymentType"     -> interestPaymentType.fold(Json.Null)(Json.fromString),
            "issueLetterUrl"          -> issueLetterUrl.fold(Json.Null)(Json.fromString),
            "interestTableUrl"        -> interestTableUrl.fold(Json.Null)(Json.fromString)
          )
      }
    )
}
