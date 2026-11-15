package dawidkobak.myfunds.piggybank.handler

import dawidkobak.myfunds.domain.DatabaseAccess
import doobie.implicits.*
import io.circe.*
import zio.*
import zio.interop.catz.*

object PiggyBankListHandler {
  def apply: RIO[DatabaseAccess, Json] =
    for {
      databaseAccess <- ZIO.service[DatabaseAccess]
      piggyBanks <-
        sql"""
              SELECT id, name, aimAmount, currentAmount, dueDate, link FROM mf_piggy_bank
              ORDER BY dueDate
           """
          .query[(Int, String, BigDecimal, BigDecimal, String, Option[String])]
          .to[List]
          .transact(databaseAccess.transactor)
    } yield Json.fromValues(
      piggyBanks.map { case (id, name, aimAmount, currentAmount, dueDate, link) =>
        Json.obj(
          "id"            -> Json.fromInt(id),
          "name"          -> Json.fromString(name),
          "aimAmount"     -> Json.fromBigDecimal(aimAmount),
          "currentAmount" -> Json.fromBigDecimal(currentAmount),
          "dueDate"       -> Json.fromString(dueDate),
          "link"          -> link.fold(Json.Null)(Json.fromString)
        )
      }
    )
}
