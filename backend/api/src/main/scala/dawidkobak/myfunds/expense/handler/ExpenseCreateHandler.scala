package dawidkobak.myfunds.expense.handler

import dawidkobak.myfunds.domain.DatabaseAccess
import dawidkobak.myfunds.expense.Expense
import doobie.implicits.*
import io.circe.*
import zio.*
import zio.interop.catz.*

object ExpenseCreateHandler {
  def apply(payload: Json, ownerName: String): RIO[DatabaseAccess, Json] =
    for {
      databaseAccess <- ZIO.service[DatabaseAccess]
      name             <- ZIO.fromEither(payload.hcursor.get[String]("name"))
      value            <- ZIO.fromEither(payload.hcursor.get[Double]("value"))
      createdAt        <- ZIO.succeed(payload.hcursor.get[String]("createdAt").toOption.getOrElse(java.time.LocalDate.now().toString))
      expenseListId    <- ZIO.fromEither(payload.hcursor.downField("expenseList").get[Int]("id"))
      color            <- ZIO.succeed(payload.hcursor.get[String]("color").toOption.getOrElse("#000000"))
      subExpenses      <- ZIO.succeed(payload.hcursor.get[List[Json]]("subExpenses").toOption.getOrElse(List.empty))
      expense <-
        ZIO.when(subExpenses.isEmpty) {
          sql"""
                  INSERT INTO mf_expense (name, value, createdat, list_id, color) VALUES (
                      $name,
                      $value,
                      $createdAt::timestamp,
                      $expenseListId,
                      $color
                  )
                  RETURNING id, name, value, createdat, list_id, color
               """
            .query[(Int, String, Double, String, Int, String)]
            .unique
            .transact(databaseAccess.transactor)
        }
      expenseWithSubexpenses <-
        ZIO.when(subExpenses.nonEmpty) {
          for {
            value <- ZIO.succeed {
              subExpenses.map { subExpense =>
                subExpense.hcursor.get[Double]("value").toOption.getOrElse(0.0)
              }.sum
            }
            expense <-
              sql"""
                    INSERT INTO mf_expense (name, value, list_id, color) VALUES (
                      $name,
                      $value,
                      $expenseListId,
                      $color
                    )
                    RETURNING id, name, value, list_id, color
                 """
                .query[(Int, String, Double, Int, String)]
                .unique
                .transact(databaseAccess.transactor)
            subExpenses <- ZIO.foreach(subExpenses) { subExpense =>
              for {
                subExpenseName      <- ZIO.fromEither(subExpense.hcursor.get[String]("name"))
                subExpenseValue     <- ZIO.fromEither(subExpense.hcursor.get[Double]("value"))
                subExpenseCreatedAt <- ZIO.fromEither(subExpense.hcursor.get[String]("createdAt"))
                subExpense <-
                  sql"""
                        INSERT INTO mf_sub_expense (name, value, createdat, expense_id) VALUES (
                          $subExpenseName,
                          $subExpenseValue,
                          $subExpenseCreatedAt::timestamp,
                          ${expense._1}
                        )
                        RETURNING id, name, value, createdat
                     """
                    .query[(Int, String, Double, String)]
                    .unique
                    .transact(databaseAccess.transactor)
              } yield subExpense
            }
          } yield (expense, subExpenses)
        }
    } yield (expenseWithSubexpenses, expense) match {
      case (Some((expense, subExpenses)), _) =>
        Json.obj(
          "id"        -> Json.fromInt(expense._1),
          "name"      -> Json.fromString(expense._2),
          "value"     -> Json.fromDoubleOrNull(expense._3),
          "createdAt" -> Json.Null,
          "color"     -> Json.fromString(expense._5),
          "subExpenses" -> Json.fromValues(
            subExpenses.map { subExpense =>
              Json.obj(
                "id"        -> Json.fromInt(subExpense._1),
                "name"      -> Json.fromString(subExpense._2),
                "value"     -> Json.fromDoubleOrNull(subExpense._3),
                "createdAt" -> Json.fromString(subExpense._4.take(10))
              )
            }
          )
        )

      case (_, Some(e)) =>
        Json.obj(
          "id"          -> Json.fromInt(e._1),
          "name"        -> Json.fromString(e._2),
          "value"       -> Json.fromDoubleOrNull(e._3),
          "createdAt"   -> Json.fromString(e._4),
          "color"       -> Json.fromString(e._6),
          "subExpenses" -> Json.fromValues(List.empty[Json])
        )

      case _ => Json.Null
    }

}
