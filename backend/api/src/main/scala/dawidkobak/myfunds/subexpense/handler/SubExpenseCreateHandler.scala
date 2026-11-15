package dawidkobak.myfunds.subexpense.handler

import dawidkobak.myfunds.domain.DatabaseAccess
import dawidkobak.myfunds.subexpense.SubExpense
import doobie.implicits.*
import io.circe.*
import zio.*
import zio.interop.catz.*

object SubExpenseCreateHandler {
  def apply(payload: Json, owner: String): RIO[DatabaseAccess, Json] =
    for {
      databaseAccess <- ZIO.service[DatabaseAccess]
      name             <- ZIO.fromEither(payload.hcursor.get[String]("name"))
      createdAt        <- ZIO.fromEither(payload.hcursor.get[String]("createdAt"))
      value            <- ZIO.fromEither(payload.hcursor.get[Double]("value"))
      expenseId        <- ZIO.fromEither(payload.hcursor.get[Int]("expenseId"))
      subExpense <-
        sql"""
              INSERT INTO mf_sub_expense (name, createdAt, value, expense_id) VALUES (
                  $name,
                  $createdAt::date,
                  $value,
                  $expenseId
              )
              RETURNING id, name, createdAt, value, expense_id
           """
          .query[SubExpense]
          .unique
          .transact(databaseAccess.transactor)
    } yield Json.obj(
      "id"        -> Json.fromInt(subExpense.id),
      "name"      -> Json.fromString(subExpense.name),
      "createdAt" -> Json.fromString(subExpense.createdAt),
      "value"     -> Json.fromDoubleOrNull(subExpense.value),
      "expenseId" -> Json.fromInt(subExpense.expenseId)
    )
}
