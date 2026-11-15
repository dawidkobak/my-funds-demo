package dawidkobak.myfunds.stockquote.handler

import dawidkobak.myfunds.domain.DatabaseAccess
import doobie.implicits.*
import io.circe.*
import zio.*
import zio.interop.catz.*

object StockQuoteDeleteHandler {
  def apply(stockQuoteId: Int): RIO[DatabaseAccess, Json] =
    for {
      databaseAccess <- ZIO.service[DatabaseAccess]
      _ <-
        sql"""
              DELETE FROM mf_stock_quote WHERE id = $stockQuoteId
           """.update.run
          .transact(databaseAccess.transactor)
    } yield Json.True
}
