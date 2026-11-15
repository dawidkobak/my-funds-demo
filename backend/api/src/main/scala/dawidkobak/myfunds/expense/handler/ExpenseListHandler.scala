package dawidkobak.myfunds.expense.handler

import dawidkobak.myfunds.domain.DatabaseAccess
import dawidkobak.myfunds.expense.Expense
import dawidkobak.myfunds.subexpense.SubExpense
import doobie.implicits.*
import io.circe.*
import zio.*
import zio.interop.catz.*

object ExpenseListHandler {
  def apply(username: String): RIO[DatabaseAccess, Json] =
    for {
      databaseAccess <- ZIO.service[DatabaseAccess]
      expenses <-
        sql"""
              SELECT id, name, value, createdAt, list_id FROM mf_expense
           """
          .query[Expense]
          .to[List]
          .transact(databaseAccess.transactor)
      subExpenses <-
        sql"""
              SELECT s.id, s.name, s.createdat, s.value, s.expense_id
              FROM mf_sub_expense s
              JOIN mf_expense e ON s.expense_id = e.id
              JOIN mf_expenses_list el on e.list_id = el.id
              JOIN mf_user u on el.owner_id = u.id
              WHERE u.username = $username
           """
          .query[SubExpense]
          .to[List]
          .transact(databaseAccess.transactor)
    } yield Json.fromValues(
      expenses.map { expense =>
        Json.obj(
          "id"          -> Json.fromInt(expense.id),
          "name"        -> Json.fromString(expense.name),
          "value"       -> Json.fromDoubleOrNull(expense.value),
          "createdAt"   -> Json.fromString(expense.createdAt),
          "expenseList" -> Json.fromInt(expense.expenseList),
          "subExpenses" -> Json.fromValues(
            subExpenses
              .filter(_.expenseId == expense.id)
              .map { subExpense =>
                Json.obj(
                  "id"        -> Json.fromInt(subExpense.id),
                  "name"      -> Json.fromString(subExpense.name),
                  "createdAt" -> Json.fromString(subExpense.createdAt),
                  "value"     -> Json.fromDoubleOrNull(subExpense.value),
                  "expenseId" -> Json.fromInt(subExpense.expenseId)
                )
              }
          )
        )
      }
    )
}
