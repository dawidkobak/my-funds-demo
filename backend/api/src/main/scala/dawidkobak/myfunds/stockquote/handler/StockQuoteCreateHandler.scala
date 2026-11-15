package dawidkobak.myfunds.stockquote.handler

import dawidkobak.myfunds.domain.DatabaseAccess
import dawidkobak.myfunds.stockquote.StockQuote
import doobie.implicits.*
import io.circe.*
import zio.*
import zio.interop.catz.*

object StockQuoteCreateHandler {
  def apply(payload: Json): RIO[DatabaseAccess, Json] =
    for {
      databaseAccess <- ZIO.service[DatabaseAccess]
      date             <- ZIO.fromEither(payload.hcursor.get[String]("date"))
      quotes           <- ZIO.fromEither(payload.hcursor.get[List[Json]]("quotes"))
      stockQuotes <- ZIO.foreach(quotes) { quote =>
        for {
          stockId <- ZIO.fromEither(quote.hcursor.get[Int]("stockId"))
          value   <- ZIO.fromEither(quote.hcursor.get[Double]("value"))
          stockQuote <-
            sql"""
                  INSERT INTO mf_stock_quote (stock_id, value, date) VALUES (
                      $stockId,
                      $value,
                      $date::date
                  )
                  RETURNING id, stock_id, value, date
               """
              .query[(Int, Int, Double, String)]
              .unique
              .transact(databaseAccess.transactor)
        } yield stockQuote
      }
    } yield Json.obj(
      "date" -> Json.fromString(date),
      "quotes" -> Json.fromValues(
        stockQuotes.map { case (id, stockId, value, date) =>
          Json.obj(
            "id"      -> Json.fromInt(id),
            "stockId" -> Json.fromInt(stockId),
            "value"   -> Json.fromDouble(value).get,
            "date"    -> Json.fromString(date)
          )
        }
      )
    )
}
