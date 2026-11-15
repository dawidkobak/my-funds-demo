package dawidkobak.myfunds.subexpense.handler

import dawidkobak.myfunds.domain.DatabaseAccess
import doobie.implicits.*
import io.circe.*
import zio.*
import zio.interop.catz.*

object SubExpenseDeleteHandler {
  def apply(subExpenseId: Int): RIO[DatabaseAccess, Json] =
    for {
      databaseAccess <- ZIO.service[DatabaseAccess]
      _ <-
        sql"""
              DELETE FROM mf_sub_expense WHERE id = $subExpenseId
           """.update.run
          .transact(databaseAccess.transactor)
    } yield Json.True
}
