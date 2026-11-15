package dawidkobak.myfunds.expense.handler

import dawidkobak.myfunds.domain.DatabaseAccess
import doobie.implicits.*
import io.circe.*
import zio.*
import zio.interop.catz.*

object ExpenseDeleteHandler {
  def apply(expenseId: Int): RIO[DatabaseAccess, Json] =
    for {
      databaseAccess <- ZIO.service[DatabaseAccess]
      _ <-
        sql"""
              DELETE FROM mf_expense WHERE id = $expenseId
           """.update.run
          .transact(databaseAccess.transactor)
    } yield Json.True
}
