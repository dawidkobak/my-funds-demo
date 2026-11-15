package dawidkobak.myfunds.bond.handler

import dawidkobak.myfunds.domain.DatabaseAccess
import doobie.implicits.*
import io.circe.Json
import zio.*
import zio.interop.catz.*

object BondWalletValueInTimeHandler {
  def apply(walletId: Int): RIO[DatabaseAccess, Json] =
    for {
      databaseAccess <- ZIO.service[DatabaseAccess]
      walletValues <-
        sql"""
              WITH wallet_date_boundaries AS (
                SELECT
                  MIN(mf_bond_position.purchase_date) AS start_date,
                  LEAST(
                    CURRENT_DATE,
                    (SELECT mf_demo_dataset_identity.as_of_date FROM mf_demo_dataset_identity WHERE mf_demo_dataset_identity.id = 1),
                    MAX(mf_bond_position.maturity_date)
                  ) AS end_date
                FROM mf_bond_position
                WHERE mf_bond_position.wallet_id = $walletId
              ),
              wallet_purchase_dates AS (
                SELECT DISTINCT
                  mf_bond_position.purchase_date AS valuation_date
                FROM mf_bond_position
                WHERE mf_bond_position.wallet_id = $walletId
              ),
              wallet_month_starts AS (
                SELECT
                  generate_series(
                    DATE_TRUNC('month', wallet_date_boundaries.start_date)::date,
                    DATE_TRUNC('month', wallet_date_boundaries.end_date)::date,
                    INTERVAL '1 month'
                  )::date AS valuation_month_start
                FROM wallet_date_boundaries
                WHERE wallet_date_boundaries.start_date IS NOT NULL
              ),
              wallet_monthly_dates AS (
                SELECT
                  LEAST((wallet_month_starts.valuation_month_start + INTERVAL '1 month - 1 day')::date, wallet_date_boundaries.end_date) AS valuation_date
                FROM wallet_month_starts
                JOIN wallet_date_boundaries ON true
              ),
              wallet_current_dates AS (
                SELECT
                  wallet_date_boundaries.end_date AS valuation_date
                FROM wallet_date_boundaries
                WHERE wallet_date_boundaries.end_date IS NOT NULL
              ),
              wallet_valuation_dates AS (
                SELECT wallet_purchase_dates.valuation_date
                FROM wallet_purchase_dates
                UNION
                SELECT wallet_monthly_dates.valuation_date
                FROM wallet_monthly_dates
                UNION
                SELECT wallet_current_dates.valuation_date
                FROM wallet_current_dates
              ),
              wallet_position_counts_by_date AS (
                SELECT
                  wallet_valuation_dates.valuation_date,
                  COUNT(mf_bond_position.id) AS position_count
                FROM wallet_valuation_dates
                JOIN mf_bond_position ON mf_bond_position.wallet_id = $walletId
                  AND mf_bond_position.purchase_date <= wallet_valuation_dates.valuation_date
                GROUP BY wallet_valuation_dates.valuation_date
              ),
              wallet_values AS (
                SELECT
                  wallet_valuation_dates.valuation_date,
                  SUM((mf_bond_position.available_units + mf_bond_position.blocked_units) * (mf_bond_issue.nominal_value + mf_bond_interest_value.accrued_interest)) AS value,
                  SUM(mf_bond_position.purchase_cost) AS own_contribution
                FROM wallet_valuation_dates
                JOIN wallet_position_counts_by_date ON wallet_position_counts_by_date.valuation_date = wallet_valuation_dates.valuation_date
                JOIN mf_bond_position ON mf_bond_position.wallet_id = $walletId
                  AND mf_bond_position.purchase_date <= wallet_valuation_dates.valuation_date
                JOIN mf_bond_issue ON mf_bond_position.bond_issue_id = mf_bond_issue.id
                LEFT JOIN mf_bond_interest_value ON mf_bond_interest_value.bond_issue_id = mf_bond_issue.id
                  AND mf_bond_interest_value.base_valuation_date = (wallet_valuation_dates.valuation_date - (mf_bond_position.purchase_date - mf_bond_issue.sale_start_date))
                GROUP BY wallet_valuation_dates.valuation_date, wallet_position_counts_by_date.position_count
                HAVING COUNT(mf_bond_interest_value.id) = wallet_position_counts_by_date.position_count
              )
              SELECT
                wallet_values.valuation_date,
                wallet_values.value,
                wallet_values.own_contribution
              FROM wallet_values
              ORDER BY wallet_values.valuation_date
           """
          .query[(String, BigDecimal, BigDecimal)]
          .to[List]
          .transact(databaseAccess.transactor)
    } yield Json.fromValues(
      walletValues.map { case (valuationDate, currentValue, ownContribution) =>
        Json.obj(
          "date"            -> Json.fromString(valuationDate.take(10)),
          "value"           -> Json.fromBigDecimal(currentValue.setScale(2, BigDecimal.RoundingMode.HALF_UP)),
          "ownContribution" -> Json.fromBigDecimal(ownContribution)
        )
      }
    )
}
