package dawidkobak.myfunds.stockquote.handler

import dawidkobak.myfunds.domain.DatabaseAccess
import dawidkobak.myfunds.stockquote.StockQuote
import doobie.implicits.*
import io.circe.*
import zio.*
import zio.interop.catz.*

object StockQuoteUpdateHandler {
  def apply(payload: Json): RIO[DatabaseAccess, Json] =
    for {
      databaseAccess <- ZIO.service[DatabaseAccess]
      id               <- ZIO.fromEither(payload.hcursor.get[Int]("id"))
      stockId          <- ZIO.fromEither(payload.hcursor.get[Int]("stockId")).option
      value            <- ZIO.fromEither(payload.hcursor.get[Double]("value")).option
      date             <- ZIO.fromEither(payload.hcursor.get[String]("date")).option
      stockQuote <-
        sql"""
              UPDATE mf_stock_quote SET
                stock_id = ${if (stockId.isDefined) stockId.get else fr"""stock_id"""},
                value = ${if (value.isDefined) value.get else fr"""value"""},
                date = ${if (date.isDefined) date.get else fr"""date"""}
              WHERE id = $id
              RETURNING id, stock_id, value, date;
           """
          .query[StockQuote]
          .unique
          .transact(databaseAccess.transactor)
    } yield Json.obj(
      "id"      -> Json.fromInt(stockQuote.id),
      "stockId" -> Json.fromInt(stockQuote.stockId),
      "value"   -> Json.fromDoubleOrNull(stockQuote.value),
      "date"    -> Json.fromString(stockQuote.date)
    )
}
