package dawidkobak.myfunds.stockquote.handler

import dawidkobak.myfunds.domain.DatabaseAccess
import dawidkobak.myfunds.stockquote.StockQuote
import doobie.implicits.*
import io.circe.*
import zio.*
import zio.interop.catz.*

object StockQuoteListHandler {
  def apply: RIO[DatabaseAccess, Json] =
    for {
      databaseAccess <- ZIO.service[DatabaseAccess]
      stockQuoteDates <-
        sql"""
              WITH stock_quote_dates AS (
                SELECT DISTINCT mf_stock_quote.date
                FROM mf_stock_quote
              )
              SELECT stock_quote_dates.date
              FROM stock_quote_dates
              ORDER BY stock_quote_dates.date DESC
           """
          .query[String]
          .to[List]
          .transact(databaseAccess.transactor)
    } yield Json.fromValues(
      stockQuoteDates.map(stockQuoteDate => Json.fromString(stockQuoteDate.take(10)))
    )
}
