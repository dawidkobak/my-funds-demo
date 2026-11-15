package dawidkobak.myfunds.subexpense.handler

import dawidkobak.myfunds.domain.DatabaseAccess
import dawidkobak.myfunds.subexpense.SubExpense
import doobie.implicits.*
import io.circe.*
import zio.*
import zio.interop.catz.*

object SubExpenseListHandler {
  def apply: RIO[DatabaseAccess, Json] =
    for {
      databaseAccess <- ZIO.service[DatabaseAccess]
      subExpenses <-
        sql"""
              SELECT id, name, createdAt, value, expense_id FROM mf_sub_expense
           """
          .query[SubExpense]
          .to[List]
          .transact(databaseAccess.transactor)
    } yield Json.fromValues(
      subExpenses.map(subExpense =>
        Json.obj(
          "id"        -> Json.fromInt(subExpense.id),
          "name"      -> Json.fromString(subExpense.name),
          "createdAt" -> Json.fromString(subExpense.createdAt),
          "value"     -> Json.fromDoubleOrNull(subExpense.value),
          "expenseId" -> Json.fromInt(subExpense.expenseId)
        )
      )
    )
}
