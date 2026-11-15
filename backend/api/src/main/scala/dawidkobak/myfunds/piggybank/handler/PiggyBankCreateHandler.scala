package dawidkobak.myfunds.piggybank.handler

import dawidkobak.myfunds.domain.DatabaseAccess
import dawidkobak.myfunds.piggybank.PiggyBank
import doobie.implicits.*
import io.circe.*
import zio.*
import zio.interop.catz.*

import java.time.LocalDate
import java.time.format.DateTimeFormatter

object PiggyBankCreateHandler {
  def apply(payload: Json, ownerName: String): RIO[DatabaseAccess, Json] =
    for {
      databaseAccess <- ZIO.service[DatabaseAccess]
      name             <- ZIO.fromEither(payload.hcursor.get[String]("name"))
      aimAmount        <- ZIO.fromEither(payload.hcursor.get[Double]("aimAmount"))
      currentAmount    <- ZIO.fromEither(payload.hcursor.get[Double]("currentAmount")).orElseSucceed(0.0)
      dueDate          <- ZIO.fromEither(payload.hcursor.get[String]("dueDate"))
      link             <- ZIO.fromEither(payload.hcursor.get[String]("link")).option
      piggyBank <-
        sql"""
              INSERT INTO mf_piggy_bank (owner_id, name, aimamount, currentamount, duedate, link) VALUES (
                 (SELECT id FROM mf_user WHERE username = $ownerName),
                  $name,
                  $aimAmount,
                  $currentAmount,
                  $dueDate::date,
                  $link
              )
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
