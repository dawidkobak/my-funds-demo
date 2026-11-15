package dawidkobak.myfunds.expenselist.handler

import dawidkobak.myfunds.domain.DatabaseAccess
import dawidkobak.myfunds.expenselist.ExpenseList
import doobie.implicits.*
import io.circe.*
import zio.*
import zio.interop.catz.*

object ExpenseListUpdateHandler {
  def apply(payload: Json, ownerName: String): RIO[DatabaseAccess, Json] =
    for {
      databaseAccess <- ZIO.service[DatabaseAccess]
      id               <- ZIO.fromEither(payload.hcursor.get[Int]("id"))
      name             <- ZIO.fromEither(payload.hcursor.get[String]("name")).option
      expenseList <-
        sql"""
              UPDATE mf_expenses_list SET
                name = ${if (name.isDefined) name.get else fr"""name"""}
              WHERE id = $id AND owner_id = (SELECT id FROM mf_user WHERE username = $ownerName)
              RETURNING id, name, createdAt;
           """
          .query[ExpenseList]
          .unique
          .transact(databaseAccess.transactor)
    } yield Json.obj(
      "id"        -> Json.fromInt(expenseList.id),
      "name"      -> Json.fromString(expenseList.name),
      "createdAt" -> Json.fromString(expenseList.createdAt)
    )
}
