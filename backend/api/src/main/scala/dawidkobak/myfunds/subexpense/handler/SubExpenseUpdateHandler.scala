package dawidkobak.myfunds.subexpense.handler

import dawidkobak.myfunds.domain.DatabaseAccess
import dawidkobak.myfunds.subexpense.SubExpense
import doobie.implicits.*
import io.circe.*
import zio.*
import zio.interop.catz.*

object SubExpenseUpdateHandler {
  def apply(payload: Json, owner: String): RIO[DatabaseAccess, Json] =
    for {
      databaseAccess <- ZIO.service[DatabaseAccess]
      id               <- ZIO.fromEither(payload.hcursor.get[Int]("id"))
      name             <- ZIO.fromEither(payload.hcursor.get[String]("name")).option
      createdAt        <- ZIO.fromEither(payload.hcursor.get[String]("createdAt")).option
      value            <- ZIO.fromEither(payload.hcursor.get[Double]("value")).option
      expenseId        <- ZIO.fromEither(payload.hcursor.get[Int]("expenseId")).option
      subExpense <-
        sql"""
              UPDATE mf_sub_expense SET
                name = ${if (name.isDefined) name.get else fr"""name"""},
                createdAt = ${if (createdAt.isDefined) fr"""${createdAt.get}::date""" else fr"""createdAt"""},
                value = ${if (value.isDefined) value.get else fr"""value"""},
                expense_id = ${if (expenseId.isDefined) expenseId.get else fr"""expense_id"""}
              WHERE id = $id
              RETURNING id, name, createdAt, value, expense_id;
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
