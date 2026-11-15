package dawidkobak.myfunds.wallet.handler

import dawidkobak.myfunds.domain.DatabaseAccess
import doobie.implicits.*
import io.circe.Json
import zio.*
import zio.interop.catz.*

object WalletValueInTimeHandler {
  def apply(walletId: Int): RIO[DatabaseAccess, Json] =
    for {
      databaseAccess <- ZIO.service[DatabaseAccess]
      walletValues <-
        sql"""
              WITH demo_valuation_boundary AS (
                SELECT LEAST(CURRENT_DATE, mf_demo_dataset_identity.as_of_date) AS end_date
                FROM mf_demo_dataset_identity
                WHERE mf_demo_dataset_identity.id = 1
              ), wallet_transactions AS (
                SELECT
                  mf_transaction.stock_id,
                  mf_stock.currency_id,
                  mf_transaction.amount,
                  mf_transaction.purchasedate,
                  mf_transaction.purchasecost,
                  mf_transaction.transactiontype
                FROM mf_transaction
                JOIN mf_stock ON mf_stock.id = mf_transaction.stock_id
                WHERE mf_transaction.wallet_id = $walletId
              ),
              wallet_event_dates AS (
                SELECT mf_stock_quote.date::date AS valuation_date
                FROM mf_stock_quote
                JOIN wallet_transactions ON wallet_transactions.stock_id = mf_stock_quote.stock_id
                WHERE mf_stock_quote.date::date <= (SELECT demo_valuation_boundary.end_date FROM demo_valuation_boundary)
                UNION
                SELECT wallet_transactions.purchasedate::date
                FROM wallet_transactions
                WHERE wallet_transactions.purchasedate::date <= (SELECT demo_valuation_boundary.end_date FROM demo_valuation_boundary)
                UNION
                SELECT mf_currency_rate.date::date
                FROM mf_currency_rate
                JOIN wallet_transactions ON wallet_transactions.currency_id = mf_currency_rate.currency_id
                WHERE mf_currency_rate.date::date <= (SELECT demo_valuation_boundary.end_date FROM demo_valuation_boundary)
                UNION
                SELECT demo_valuation_boundary.end_date FROM demo_valuation_boundary
                WHERE EXISTS (
                  SELECT 1
                  FROM wallet_transactions
                  WHERE wallet_transactions.purchasedate::date <= (SELECT demo_valuation_boundary.end_date FROM demo_valuation_boundary)
                )
              ),
              wallet_valuation_dates AS (
                SELECT wallet_event_dates.valuation_date
                FROM wallet_event_dates
                WHERE wallet_event_dates.valuation_date >= (
                  SELECT MIN(wallet_transactions.purchasedate::date)
                  FROM wallet_transactions
                )
              ),
              wallet_holdings AS (
                SELECT
                  wallet_valuation_dates.valuation_date,
                  wallet_transactions.stock_id,
                  wallet_transactions.currency_id,
                  SUM(
                    CASE
                      WHEN wallet_transactions.transactiontype = 'BUY' THEN wallet_transactions.amount
                      ELSE -wallet_transactions.amount
                    END
                  ) AS amount,
                  SUM(
                    CASE
                      WHEN wallet_transactions.transactiontype = 'BUY' THEN wallet_transactions.amount
                      ELSE 0
                    END
                  ) AS bought_amount,
                  SUM(
                    CASE
                      WHEN wallet_transactions.transactiontype = 'BUY' THEN wallet_transactions.purchasecost
                      ELSE 0
                    END
                  ) AS bought_cost,
                  MIN(wallet_transactions.purchasedate) FILTER (
                    WHERE wallet_transactions.transactiontype = 'BUY'
                  ) AS first_purchase_date
                FROM wallet_valuation_dates
                JOIN wallet_transactions ON wallet_transactions.purchasedate::date <= wallet_valuation_dates.valuation_date
                GROUP BY wallet_valuation_dates.valuation_date, wallet_transactions.stock_id, wallet_transactions.currency_id
              ),
              wallet_stock_values AS (
                SELECT
                  wallet_holdings.valuation_date,
                  SUM(
                    CASE
                      WHEN wallet_latest_quote.value IS NULL THEN
                        COALESCE(wallet_holdings.amount * wallet_holdings.bought_cost / NULLIF(wallet_holdings.bought_amount, 0), 0)
                      ELSE
                        wallet_holdings.amount * wallet_latest_quote.value * COALESCE(wallet_latest_currency_rate.rate, 1)
                    END
                  ) AS value
                FROM wallet_holdings
                LEFT JOIN LATERAL (
                  SELECT mf_stock_quote.value
                  FROM mf_stock_quote
                  WHERE mf_stock_quote.stock_id = wallet_holdings.stock_id
                    AND mf_stock_quote.date::date <= wallet_holdings.valuation_date
                    AND mf_stock_quote.date::date >= wallet_holdings.first_purchase_date::date
                  ORDER BY mf_stock_quote.date DESC, mf_stock_quote.id DESC
                  LIMIT 1
                ) AS wallet_latest_quote ON true
                LEFT JOIN LATERAL (
                  SELECT mf_currency_rate.rate
                  FROM mf_currency_rate
                  WHERE mf_currency_rate.currency_id = wallet_holdings.currency_id
                    AND mf_currency_rate.date::date <= wallet_holdings.valuation_date
                  ORDER BY mf_currency_rate.date DESC, mf_currency_rate.id DESC
                  LIMIT 1
                ) AS wallet_latest_currency_rate ON true
                GROUP BY wallet_holdings.valuation_date
              ),
              wallet_contributions AS (
                SELECT
                  wallet_valuation_dates.valuation_date,
                  COALESCE(
                    SUM(
                      CASE
                        WHEN wallet_transactions.transactiontype = 'BUY' THEN wallet_transactions.purchasecost
                        ELSE -wallet_transactions.purchasecost
                      END
                    ),
                    0
                  ) AS own_contribution
                FROM wallet_valuation_dates
                LEFT JOIN wallet_transactions ON wallet_transactions.purchasedate::date <= wallet_valuation_dates.valuation_date
                GROUP BY wallet_valuation_dates.valuation_date
              )
              SELECT
                wallet_valuation_dates.valuation_date,
                COALESCE(wallet_stock_values.value, 0),
                wallet_contributions.own_contribution
              FROM wallet_valuation_dates
              LEFT JOIN wallet_stock_values ON wallet_stock_values.valuation_date = wallet_valuation_dates.valuation_date
              JOIN wallet_contributions ON wallet_contributions.valuation_date = wallet_valuation_dates.valuation_date
              ORDER BY wallet_valuation_dates.valuation_date
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
