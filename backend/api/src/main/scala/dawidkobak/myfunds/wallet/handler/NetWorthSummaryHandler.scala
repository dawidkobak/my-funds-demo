package dawidkobak.myfunds.wallet.handler

import dawidkobak.myfunds.domain.DatabaseAccess
import doobie.implicits.*
import io.circe.Json
import zio.*
import zio.interop.catz.*

object NetWorthSummaryHandler {
  def apply(valuationDate: String): RIO[DatabaseAccess, Json] =
    for {
      databaseAccess <- ZIO.service[DatabaseAccess]
      stockWalletValues <-
        sql"""
              WITH latest_stock_quotes AS (
                SELECT DISTINCT ON (mf_stock_quote.stock_id)
                  mf_stock_quote.stock_id,
                  mf_stock_quote.value,
                  mf_stock_quote.date
                FROM mf_stock_quote
                WHERE mf_stock_quote.date <= $valuationDate::date
                ORDER BY mf_stock_quote.stock_id, mf_stock_quote.date DESC, mf_stock_quote.id DESC
              ),
              stock_holdings AS (
                SELECT
                  mf_wallet.id AS wallet_id,
                  mf_wallet.name AS wallet_name,
                  mf_stock.id AS stock_id,
                  mf_stock.currency_id AS currency_id,
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
                FROM mf_wallet
                JOIN mf_transaction ON mf_transaction.wallet_id = mf_wallet.id
                JOIN mf_stock ON mf_stock.id = mf_transaction.stock_id
                WHERE mf_wallet.name IN ('Emerytalny', 'Spekulacyjny')
                  AND mf_transaction.purchasedate <= $valuationDate::date
                GROUP BY mf_wallet.id, mf_wallet.name, mf_stock.id, mf_stock.currency_id
              )
              SELECT
                stock_holdings.wallet_name,
                COALESCE(
                  SUM(
                    CASE
                      WHEN latest_stock_quotes.value IS NULL
                        OR latest_stock_quotes.date::date < stock_holdings.first_purchase_date::date THEN
                        COALESCE(stock_holdings.amount * stock_holdings.bought_cost / NULLIF(stock_holdings.bought_amount, 0), 0)
                      ELSE
                        stock_holdings.amount * latest_stock_quotes.value * COALESCE(mf_currency_rate.rate, 1.0)
                    END
                  ),
                  0
                )
              FROM stock_holdings
              LEFT JOIN latest_stock_quotes ON latest_stock_quotes.stock_id = stock_holdings.stock_id
              LEFT JOIN mf_currency_rate ON mf_currency_rate.currency_id = stock_holdings.currency_id
                AND mf_currency_rate.date = (
                  SELECT MAX(mf_currency_rate.date)
                  FROM mf_currency_rate
                  WHERE mf_currency_rate.currency_id = stock_holdings.currency_id
                    AND mf_currency_rate.date <= $valuationDate::date
                )
              GROUP BY stock_holdings.wallet_name
              ORDER BY stock_holdings.wallet_name
           """
          .query[(String, BigDecimal)]
          .to[List]
          .transact(databaseAccess.transactor)
      bondWalletValue <-
        sql"""
              SELECT COALESCE(
                SUM(
                  (mf_bond_position.available_units + mf_bond_position.blocked_units) *
                  (mf_bond_issue.nominal_value + COALESCE(mf_bond_interest_value.accrued_interest, 0))
                ),
                0
              )
              FROM mf_bond_position
              JOIN mf_wallet ON mf_wallet.id = mf_bond_position.wallet_id
              JOIN mf_bond_issue ON mf_bond_issue.id = mf_bond_position.bond_issue_id
              LEFT JOIN mf_bond_interest_value ON mf_bond_interest_value.bond_issue_id = mf_bond_issue.id
                AND mf_bond_interest_value.base_valuation_date = (
                  $valuationDate::date - (mf_bond_position.purchase_date - mf_bond_issue.sale_start_date)
                )
              WHERE mf_wallet.name = 'Obligacje'
                AND mf_bond_position.purchase_date <= $valuationDate::date
           """
          .query[BigDecimal]
          .unique
          .transact(databaseAccess.transactor)
          .map(_.setScale(2, BigDecimal.RoundingMode.HALF_UP))
      retirementWalletValue = stockWalletValues.find(_._1 == "Emerytalny").map(_._2.setScale(2, BigDecimal.RoundingMode.HALF_UP)).getOrElse(BigDecimal(0))
      speculativeWalletValue = stockWalletValues.find(_._1 == "Spekulacyjny").map(_._2.setScale(2, BigDecimal.RoundingMode.HALF_UP)).getOrElse(BigDecimal(0))
      netWorthValue = bondWalletValue + retirementWalletValue + speculativeWalletValue
    } yield Json.obj(
      "date"       -> Json.fromString(valuationDate),
      "totalValue" -> Json.fromBigDecimal(netWorthValue),
      "categories" -> Json.fromValues(
        List(
          ("Obligacje", bondWalletValue, "#2563EB"),
          ("Emerytalny", retirementWalletValue, "#15803D"),
          ("Spekulacyjny", speculativeWalletValue, "#D97706")
        ).map { case (categoryName, categoryValue, categoryColor) =>
          Json.obj(
            "name"  -> Json.fromString(categoryName),
            "value" -> Json.fromBigDecimal(categoryValue),
            "color" -> Json.fromString(categoryColor)
          )
        }
      )
    )
}
