package dawidkobak.myfunds.expense.handler

import dawidkobak.myfunds.domain.DatabaseAccess
import dawidkobak.myfunds.expense.Expense
import doobie.implicits.*
import io.circe.*
import zio.*
import zio.interop.catz.*

object ExpenseUpdateHandler {
  def apply(payload: Json, ownerName: String): RIO[DatabaseAccess, Json] =
    for {
      databaseAccess <- ZIO.service[DatabaseAccess]
      id               <- ZIO.fromEither(payload.hcursor.get[Int]("id"))
      name             <- ZIO.fromEither(payload.hcursor.get[String]("name")).option
      value            <- ZIO.fromEither(payload.hcursor.get[Double]("value")).option
      createdAt        <- ZIO.fromEither(payload.hcursor.get[String]("createdAt")).option
      expenseList      <- ZIO.fromEither(payload.hcursor.get[Int]("expenseList")).option
      expense <-
        sql"""
              UPDATE mf_expense SET
                name = ${if (name.isDefined) name.get else fr"""name"""},
                value = ${if (value.isDefined) value.get else fr"""value"""},
                createdAt = ${if (createdAt.isDefined) fr"""${createdAt.get}::timestamp""" else fr"""createdAt"""},
                list_id = ${if (expenseList.isDefined) expenseList.get else fr"""list_id"""}
              WHERE id = $id
              RETURNING id, name, value, createdAt, list_id;
           """
          .query[Expense]
          .unique
          .transact(databaseAccess.transactor)
    } yield Json.obj(
      "id"          -> Json.fromInt(expense.id),
      "name"        -> Json.fromString(expense.name),
      "value"       -> Json.fromDoubleOrNull(expense.value),
      "createdAt"   -> Json.fromString(expense.createdAt),
      "expenseList" -> Json.fromInt(expense.expenseList)
    )
}
