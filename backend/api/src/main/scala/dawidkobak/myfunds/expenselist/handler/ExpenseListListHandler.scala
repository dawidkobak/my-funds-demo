package dawidkobak.myfunds.expenselist.handler

import dawidkobak.myfunds.domain.DatabaseAccess
import doobie.implicits.*
import io.circe.*
import zio.*
import zio.interop.catz.*

object ExpenseListListHandler {
  def apply: RIO[DatabaseAccess, Json] =
    for {
      databaseAccess <- ZIO.service[DatabaseAccess]
      expenseLists <-
        sql"""
              SELECT id, name, createdAt FROM mf_expenses_list
           """
          .query[(Int, String, String)]
          .to[List]
          .transact(databaseAccess.transactor)
    } yield Json.fromValues(
      expenseLists.map { case (id, name, createdAt) =>
        Json.obj(
          "id"        -> Json.fromInt(id),
          "name"      -> Json.fromString(name),
          "createdAt" -> Json.fromString(createdAt)
        )
      }
    )
}
