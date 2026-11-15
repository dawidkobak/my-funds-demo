package dawidkobak.myfunds.stock.handler

import dawidkobak.myfunds.domain.DatabaseAccess
import dawidkobak.myfunds.stock.Stock
import doobie.implicits.*
import io.circe.*
import zio.*
import zio.interop.catz.*

object StockCreateHandler {
  def apply(payload: Json): RIO[DatabaseAccess, Json] =
    for {
      databaseAccess <- ZIO.service[DatabaseAccess]
      name             <- ZIO.fromEither(payload.hcursor.get[String]("name"))
      color            <- ZIO.fromEither(payload.hcursor.get[String]("color"))
      stock <-
        sql"""
              INSERT INTO mf_stock  (name, color) VALUES ($name, $color)
              RETURNING id, name, color;
           """
          .query[(Int, String, String)]
          .unique
          .transact(databaseAccess.transactor)
    } yield Json.obj(
      "id"    -> Json.fromInt(stock._1),
      "name"  -> Json.fromString(stock._2),
      "color" -> Json.fromString(stock._3)
    )
}
