package dawidkobak.myfunds.piggybank.handler

import dawidkobak.myfunds.domain.DatabaseAccess
import dawidkobak.myfunds.piggybank.PiggyBank
import doobie.implicits.*
import io.circe.*
import zio.*
import zio.interop.catz.*

object PiggyBankUpdateHandler {
  def apply(payload: Json, ownerName: String): RIO[DatabaseAccess, Json] =
    for {
      databaseAccess <- ZIO.service[DatabaseAccess]
      id               <- ZIO.fromEither(payload.hcursor.get[Int]("id"))
      name             <- ZIO.fromEither(payload.hcursor.get[String]("name")).option
      aimAmount        <- ZIO.fromEither(payload.hcursor.get[Double]("aimAmount")).option
      currentAmount    <- ZIO.fromEither(payload.hcursor.get[Double]("currentAmount")).option
      dueDate          <- ZIO.fromEither(payload.hcursor.get[String]("dueDate")).option
      link             <- ZIO.fromEither(payload.hcursor.get[String]("link")).option
      piggyBank <-
        sql"""
              UPDATE mf_piggy_bank SET
                name = ${if (name.isDefined) name.get else fr"""name"""},
                aimamount = ${if (aimAmount.isDefined) aimAmount.get else fr"""aimamount"""},
                currentamount = ${if (currentAmount.isDefined) currentAmount.get else fr"""currentamount"""},
                duedate = ${if (dueDate.isDefined) fr"""${dueDate.get}::date""" else fr"""duedate"""},
                link = ${if (link.isDefined) link.get else fr"""link"""}
              WHERE id = $id
              RETURNING id, name, aimamount, currentamount, duedate, link;
           """
          .query[PiggyBank]
          .unique
          .transact(databaseAccess.transactor)
    } yield Json.obj(
      "id"            -> Json.fromInt(piggyBank.id),
      "name"          -> Json.fromString(piggyBank.name),
      "aimAmount"     -> Json.fromDoubleOrNull(piggyBank.aimAmount),
      "currentAmount" -> Json.fromDoubleOrNull(piggyBank.currentAmount),
      "dueDate"       -> Json.fromString(piggyBank.dueDate),
      "link"          -> piggyBank.link.fold(Json.Null)(Json.fromString)
    )
}
