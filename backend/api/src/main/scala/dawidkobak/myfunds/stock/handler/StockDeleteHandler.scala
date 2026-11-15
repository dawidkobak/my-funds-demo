package dawidkobak.myfunds.stock.handler

import dawidkobak.myfunds.domain.DatabaseAccess
import doobie.implicits.*
import io.circe.*
import zio.*
import zio.interop.catz.*

object StockDeleteHandler {
  def apply(stockId: Int): RIO[DatabaseAccess, Json] =
    for {
      databaseAccess <- ZIO.service[DatabaseAccess]
      _ <-
        sql"""
              DELETE FROM mf_stock WHERE id = $stockId
           """.update.run
          .transact(databaseAccess.transactor)
    } yield Json.True
}
