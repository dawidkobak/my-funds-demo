package dawidkobak.myfunds.expenselist.handler

import dawidkobak.myfunds.domain.DatabaseAccess
import doobie.implicits.*
import io.circe.*
import zio.*
import zio.interop.catz.*

object ExpenseListDeleteHandler {
  def apply(expenseListId: Int): RIO[DatabaseAccess, Json] =
    for {
      databaseAccess <- ZIO.service[DatabaseAccess]
      _ <-
        sql"""
              DELETE FROM mf_expenses_list WHERE id = $expenseListId
           """.update.run
          .transact(databaseAccess.transactor)
    } yield Json.True
}
