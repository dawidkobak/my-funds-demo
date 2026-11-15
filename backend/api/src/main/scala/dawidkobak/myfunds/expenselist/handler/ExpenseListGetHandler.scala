package dawidkobak.myfunds.expenselist.handler

import dawidkobak.myfunds.domain.DatabaseAccess
import doobie.implicits.*
import io.circe.*
import zio.*
import zio.interop.catz.*

object ExpenseListGetHandler {
  def apply(listId: Int): RIO[DatabaseAccess, Json] =
    for {
      databaseAccess <- ZIO.service[DatabaseAccess]
      expenses <-
        sql"""
              SELECT id, name, value, color, createdat
              FROM mf_expense
              WHERE list_id = $listId
           """
          .query[(Int, String, Double, Option[String], Option[String])]
          .to[List]
          .transact(databaseAccess.transactor)
      subExpenses <-
        sql"""
              SELECT id, name, createdat, value, expense_id
              FROM mf_sub_expense
              WHERE expense_id IN (SELECT id FROM mf_expense WHERE list_id = $listId)
           """
          .query[(Int, String, String, Double, Int)]
          .to[List]
          .transact(databaseAccess.transactor)
    } yield Json.fromValues(
      expenses.map { case (id, name, value, color, createdAt) =>
        Json.obj(
          "id"        -> Json.fromInt(id),
          "name"      -> Json.fromString(name),
          "createdAt" -> createdAt.fold(Json.Null)(c => Json.fromString(c.take(10))),
          "value"     -> Json.fromDoubleOrNull(value),
          "color"     -> color.fold(Json.Null)(Json.fromString),
          "subExpenses" -> Json.fromValues(
            subExpenses
              .filter(_._5 == id)
              .map { case (id, name, createdAt, value, _) =>
                Json.obj(
                  "id"        -> Json.fromInt(id),
                  "name"      -> Json.fromString(name),
                  "createdAt" -> Json.fromString(createdAt.take(10)),
                  "value"     -> Json.fromDoubleOrNull(value)
                )
              }
          )
        )
      }
    )
}
