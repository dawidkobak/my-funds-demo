package dawidkobak.myfunds.expenselist.handler

import dawidkobak.myfunds.domain.DatabaseAccess
import dawidkobak.myfunds.expenselist.ExpenseList
import doobie.implicits.*
import io.circe.*
import zio.*
import zio.interop.catz.*

object ExpenseListCreateHandler {
  def apply(payload: Json, ownerName: String): RIO[DatabaseAccess, Json] =
    for {
      databaseAccess <- ZIO.service[DatabaseAccess]
      name             <- ZIO.fromEither(payload.hcursor.get[String]("name"))
      expenseList <-
        sql"""
              INSERT INTO mf_expenses_list (owner_id, name, createdat) VALUES (
                 (SELECT id FROM mf_user WHERE username = $ownerName),
                 $name,
                 NOW()
              )
              RETURNING id, name, createdat;
           """
          .query[ExpenseList]
          .unique
          .transact(databaseAccess.transactor)
    } yield Json.obj(
      "id"        -> Json.fromInt(expenseList.id),
      "name"      -> Json.fromString(expenseList.name),
      "createdat" -> Json.fromString(expenseList.createdAt)
    )
}
