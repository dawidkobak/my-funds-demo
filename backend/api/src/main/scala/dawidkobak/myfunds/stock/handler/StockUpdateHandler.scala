package dawidkobak.myfunds.stock.handler

import dawidkobak.myfunds.domain.DatabaseAccess
import dawidkobak.myfunds.stock.Stock
import doobie.implicits.*
import io.circe.*
import zio.*
import zio.interop.catz.*

object StockUpdateHandler {
  def apply(payload: Json): RIO[DatabaseAccess, Json] =
    for {
      databaseAccess <- ZIO.service[DatabaseAccess]
      id               <- ZIO.fromEither(payload.hcursor.get[Int]("id"))
      name             <- ZIO.fromEither(payload.hcursor.get[String]("name")).option
      color            <- ZIO.fromEither(payload.hcursor.get[String]("color")).option
      stock <-
        sql"""
              UPDATE mf_stock SET
                name = ${if (name.isDefined) name.get else fr"""name"""},
                color = ${if (color.isDefined) color.get else fr"""color"""}
              WHERE id = $id
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
