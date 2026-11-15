package dawidkobak.myfunds.stock.handler

import dawidkobak.myfunds.domain.DatabaseAccess
import dawidkobak.myfunds.stock.Stock
import doobie.implicits.*
import io.circe.*
import zio.*
import zio.interop.catz.*

object StockListHandler {
  def apply: RIO[DatabaseAccess, Json] =
    for {
      databaseAccess <- ZIO.service[DatabaseAccess]
      stocks <-
        sql"""
              SELECT id, name, color FROM mf_stock
           """
          .query[(Int, String, Option[String])]
          .to[List]
          .transact(databaseAccess.transactor)
    } yield Json.fromValues(
      stocks.map { case (id, name, color) =>
        Json.obj(
          "id"    -> Json.fromInt(id),
          "name"  -> Json.fromString(name),
          "color" -> color.fold(Json.Null)(Json.fromString)
        )
      }
    )
}
