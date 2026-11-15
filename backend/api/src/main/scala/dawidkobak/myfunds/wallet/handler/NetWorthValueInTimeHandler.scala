package dawidkobak.myfunds.wallet.handler

import dawidkobak.myfunds.domain.DatabaseAccess
import doobie.implicits.*
import io.circe.Json
import zio.*
import zio.interop.catz.*

object NetWorthValueInTimeHandler {
  def apply: RIO[DatabaseAccess, Json] =
    for {
      databaseAccess <- ZIO.service[DatabaseAccess]
      netWorthValues <-
        sql"""
              WITH net_worth_date_boundaries AS (
                SELECT
                  LEAST(
                    (
                      SELECT MIN(mf_transaction.purchasedate)
                      FROM mf_transaction
                      JOIN mf_wallet ON mf_wallet.id = mf_transaction.wallet_id
                      WHERE mf_wallet.name IN ('Emerytalny', 'Spekulacyjny')
                    ),
                    (
                      SELECT MIN(mf_bond_position.purchase_date)
                      FROM mf_bond_position
                      JOIN mf_wallet ON mf_wallet.id = mf_bond_position.wallet_id
                      WHERE mf_wallet.name = 'Obligacje'
                    )
                  ) AS start_date,
                  LEAST(CURRENT_DATE, (SELECT mf_demo_dataset_identity.as_of_date
                    FROM mf_demo_dataset_identity WHERE mf_demo_dataset_identity.id = 1)) AS end_date
              ),
              net_worth_month_starts AS (
                SELECT
                  generate_series(
                    DATE_TRUNC('month', net_worth_date_boundaries.start_date)::date,
                    DATE_TRUNC('month', net_worth_date_boundaries.end_date)::date,
                    INTERVAL '1 month'
                  )::date AS valuation_month_start
                FROM net_worth_date_boundaries
                WHERE net_worth_date_boundaries.start_date IS NOT NULL
                  AND net_worth_date_boundaries.end_date IS NOT NULL
              ),
              net_worth_valuation_dates AS (
                SELECT
                  LEAST(
                    (net_worth_month_starts.valuation_month_start + INTERVAL '1 month - 1 day')::date,
                    net_worth_date_boundaries.end_date
                  ) AS valuation_date
                FROM net_worth_month_starts
                JOIN net_worth_date_boundaries ON true
                UNION
                SELECT mf_transaction.purchasedate
                FROM mf_transaction
                JOIN mf_wallet ON mf_wallet.id = mf_transaction.wallet_id
                JOIN net_worth_date_boundaries ON true
                WHERE mf_wallet.name IN ('Emerytalny', 'Spekulacyjny')
                  AND mf_transaction.purchasedate <= net_worth_date_boundaries.end_date
                UNION
                SELECT mf_bond_position.purchase_date
                FROM mf_bond_position
                JOIN mf_wallet ON mf_wallet.id = mf_bond_position.wallet_id
                JOIN net_worth_date_boundaries ON true
                WHERE mf_wallet.name = 'Obligacje'
                  AND mf_bond_position.purchase_date <= net_worth_date_boundaries.end_date
                UNION
                SELECT DISTINCT mf_stock_quote.date::date
                FROM mf_stock_quote
                JOIN mf_transaction ON mf_transaction.stock_id = mf_stock_quote.stock_id
                JOIN mf_wallet ON mf_wallet.id = mf_transaction.wallet_id
                JOIN net_worth_date_boundaries ON true
                WHERE mf_wallet.name IN ('Emerytalny', 'Spekulacyjny')
                  AND mf_stock_quote.date::date <= net_worth_date_boundaries.end_date
              ),
              net_worth_stock_holdings AS (
                SELECT
                  net_worth_valuation_dates.valuation_date,
                  mf_wallet.id AS wallet_id,
                  mf_stock.id AS stock_id,
                  mf_stock.currency_id,
                  SUM(
                    CASE
                      WHEN mf_transaction.transactiontype = 'BUY' THEN mf_transaction.amount
                      ELSE -mf_transaction.amount
                    END
                  ) AS amount,
                  SUM(
                    CASE
                      WHEN mf_transaction.transactiontype = 'BUY' THEN mf_transaction.amount
                      ELSE 0
                    END
                  ) AS bought_amount,
                  SUM(
                    CASE
                      WHEN mf_transaction.transactiontype = 'BUY' THEN mf_transaction.purchasecost
                      ELSE 0
                    END
                  ) AS bought_cost,
                  MIN(mf_transaction.purchasedate) FILTER (
                    WHERE mf_transaction.transactiontype = 'BUY'
                  ) AS first_purchase_date
                FROM net_worth_valuation_dates
                JOIN mf_transaction ON mf_transaction.purchasedate <= net_worth_valuation_dates.valuation_date
                JOIN mf_wallet ON mf_wallet.id = mf_transaction.wallet_id
                  AND mf_wallet.name IN ('Emerytalny', 'Spekulacyjny')
                JOIN mf_stock ON mf_stock.id = mf_transaction.stock_id
                GROUP BY net_worth_valuation_dates.valuation_date, mf_wallet.id, mf_stock.id, mf_stock.currency_id
              ),
              net_worth_stock_values AS (
                SELECT
                  net_worth_stock_holdings.valuation_date,
                  COALESCE(
                    SUM(
                      CASE
                        WHEN net_worth_latest_quote.value IS NULL THEN
                          COALESCE(net_worth_stock_holdings.amount * net_worth_stock_holdings.bought_cost / NULLIF(net_worth_stock_holdings.bought_amount, 0), 0)
                        ELSE
                          net_worth_stock_holdings.amount * net_worth_latest_quote.value * COALESCE(net_worth_latest_currency_rate.rate, 1)
                      END
                    ),
                    0
                  ) AS value
                FROM net_worth_stock_holdings
                LEFT JOIN LATERAL (
                  SELECT mf_stock_quote.value
                  FROM mf_stock_quote
                  WHERE mf_stock_quote.stock_id = net_worth_stock_holdings.stock_id
                    AND mf_stock_quote.date::date <= net_worth_stock_holdings.valuation_date
                    AND mf_stock_quote.date::date >= net_worth_stock_holdings.first_purchase_date::date
                  ORDER BY mf_stock_quote.date DESC, mf_stock_quote.id DESC
                  LIMIT 1
                ) AS net_worth_latest_quote ON true
                LEFT JOIN LATERAL (
                  SELECT mf_currency_rate.rate
                  FROM mf_currency_rate
                  WHERE mf_currency_rate.currency_id = net_worth_stock_holdings.currency_id
                    AND mf_currency_rate.date::date <= net_worth_stock_holdings.valuation_date
                  ORDER BY mf_currency_rate.date DESC, mf_currency_rate.id DESC
                  LIMIT 1
                ) AS net_worth_latest_currency_rate ON true
                GROUP BY net_worth_stock_holdings.valuation_date
              ),
              net_worth_stock_contributions AS (
                SELECT
                  net_worth_valuation_dates.valuation_date,
                  COALESCE(
                    SUM(
                      CASE
                        WHEN mf_transaction.transactiontype = 'BUY' THEN mf_transaction.purchasecost
                        ELSE -mf_transaction.purchasecost
                      END
                    ),
                    0
                  ) AS own_contribution
                FROM net_worth_valuation_dates
                LEFT JOIN mf_transaction ON mf_transaction.purchasedate <= net_worth_valuation_dates.valuation_date
                LEFT JOIN mf_wallet ON mf_wallet.id = mf_transaction.wallet_id
                  AND mf_wallet.name IN ('Emerytalny', 'Spekulacyjny')
                WHERE mf_wallet.id IS NOT NULL
                GROUP BY net_worth_valuation_dates.valuation_date
              ),
              net_worth_bond_values AS (
                SELECT
                  net_worth_valuation_dates.valuation_date,
                  COALESCE(
                    SUM(
                      (mf_bond_position.available_units + mf_bond_position.blocked_units) *
                      (
                        mf_bond_issue.nominal_value +
                        COALESCE(
                          (
                            SELECT mf_bond_interest_value.accrued_interest
                            FROM mf_bond_interest_value
                            WHERE mf_bond_interest_value.bond_issue_id = mf_bond_issue.id
                              AND mf_bond_interest_value.base_valuation_date = (
                                net_worth_valuation_dates.valuation_date -
                                (mf_bond_position.purchase_date - mf_bond_issue.sale_start_date)
                              )
                            LIMIT 1
                          ),
                          0
                        )
                      )
                    ),
                    0
                  ) AS value,
                  COALESCE(SUM(mf_bond_position.purchase_cost), 0) AS own_contribution
                FROM net_worth_valuation_dates
                LEFT JOIN mf_bond_position ON mf_bond_position.purchase_date <= net_worth_valuation_dates.valuation_date
                LEFT JOIN mf_wallet ON mf_wallet.id = mf_bond_position.wallet_id
                  AND mf_wallet.name = 'Obligacje'
                LEFT JOIN mf_bond_issue ON mf_bond_issue.id = mf_bond_position.bond_issue_id
                WHERE mf_wallet.id IS NOT NULL
                GROUP BY net_worth_valuation_dates.valuation_date
              )
              SELECT
                net_worth_valuation_dates.valuation_date,
                COALESCE(net_worth_stock_values.value, 0) + COALESCE(net_worth_bond_values.value, 0),
                COALESCE(net_worth_stock_contributions.own_contribution, 0) + COALESCE(net_worth_bond_values.own_contribution, 0)
              FROM net_worth_valuation_dates
              LEFT JOIN net_worth_stock_values ON net_worth_stock_values.valuation_date = net_worth_valuation_dates.valuation_date
              LEFT JOIN net_worth_stock_contributions ON net_worth_stock_contributions.valuation_date = net_worth_valuation_dates.valuation_date
              LEFT JOIN net_worth_bond_values ON net_worth_bond_values.valuation_date = net_worth_valuation_dates.valuation_date
              ORDER BY net_worth_valuation_dates.valuation_date
           """
          .query[(String, BigDecimal, BigDecimal)]
          .to[List]
          .transact(databaseAccess.transactor)
    } yield Json.fromValues(
      netWorthValues.map { case (valuationDate, currentValue, ownContribution) =>
        Json.obj(
          "date"            -> Json.fromString(valuationDate.take(10)),
          "value"           -> Json.fromBigDecimal(currentValue.setScale(2, BigDecimal.RoundingMode.HALF_UP)),
          "ownContribution" -> Json.fromBigDecimal(ownContribution)
        )
      }
    )
}
