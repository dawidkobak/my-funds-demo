package dawidkobak.myfunds.stockquote.handler

import dawidkobak.myfunds.domain.DatabaseAccess
import dawidkobak.myfunds.stockquote.StockQuote
import doobie.implicits.*
import io.circe.*
import zio.*
import zio.interop.catz.*

object StockQuoteGetHandler {
  def apply(stockQuoteDate: String): RIO[DatabaseAccess, Json] =
    for {
      databaseAccess <- ZIO.service[DatabaseAccess]
      stockQuotes <-
        sql"""
                SELECT DISTINCT ON (mf_stock_quote.stock_id)
                       mf_stock_quote.id, mf_stock.id, mf_stock_quote.value, mf_stock_quote.date, mf_stock.name,
                       COALESCE(mf_currency_rate.rate, 1.0) as currency_rate
                FROM mf_stock_quote
                JOIN mf_stock ON mf_stock_quote.stock_id = mf_stock.id
                LEFT JOIN mf_currency ON mf_stock.currency_id = mf_currency.id
                LEFT JOIN mf_currency_rate ON mf_currency_rate.currency_id = mf_currency.id
                    AND mf_currency_rate.date = (
                        SELECT MAX(mf_currency_rate.date)
                        FROM mf_currency_rate
                        WHERE mf_currency_rate.currency_id = mf_currency.id
                          AND mf_currency_rate.date <= $stockQuoteDate::date
                    )
                WHERE mf_stock_quote.date <= $stockQuoteDate::date
                ORDER BY mf_stock_quote.stock_id, mf_stock_quote.date DESC, mf_stock_quote.id DESC
             """
          .query[(Int, Int, Double, String, String, Double)]
          .to[List]
          .transact(databaseAccess.transactor)

    } yield Json.obj(
      "date" -> Json.fromString(stockQuoteDate),
      "quotes" -> Json.fromValues(
        stockQuotes.map { case (id, stockId, value, date, name, currencyRate) =>
          Json.obj(
            "id" -> Json.fromInt(id),
            "stock" -> Json.obj(
              "id"   -> Json.fromInt(stockId),
              "name" -> Json.fromString(name)
            ),
            "value" -> Json.fromDoubleOrNull(value * currencyRate),
            "date"  -> Json.fromString(date)
          )
        }
      )
    )
}
