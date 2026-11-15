package dawidkobak.myfunds.expenselist

import dawidkobak.myfunds.domain.DatabaseAccess
import dawidkobak.myfunds.domain.helper.*
import dawidkobak.myfunds.domain.helper.RequestJsonBody.*
import dawidkobak.myfunds.expense.handler.{ExpenseCreateHandler, ExpenseDeleteHandler, ExpenseListHandler, ExpenseUpdateHandler}
import dawidkobak.myfunds.expenselist.handler.{ExpenseListCreateHandler, ExpenseListDeleteHandler, ExpenseListGetHandler, ExpenseListListHandler, ExpenseListUpdateHandler}
import sttp.client3.httpclient.zio.{HttpClientZioBackend, SttpClient}
import zio.*
import zio.http.*

object ExpenseListModule {
  def httpApp: Routes[DatabaseAccess, Throwable] =
    Routes(
      Method.GET / "api" / "expense-list" / "list" -> handler { (request: Request) =>
        for {
          response <- ExpenseListListHandler.apply
        } yield Response.json(response.noSpaces)
      },
      Method.POST / "api" / "expense-list" / "create" -> handler { (request: Request) =>
        for {
          payload  <- request.readJson
          owner    <- ZIO.succeed("test")
          response <- ExpenseListCreateHandler(payload, owner)
        } yield Response.json(response.noSpaces)
      },
      Method.PATCH / "api" / "expense-list" / "update" -> handler { (request: Request) =>
        for {
          payload  <- request.readJson
          owner    <- ZIO.succeed("test")
          response <- ExpenseListUpdateHandler(payload, owner)
        } yield Response.json(response.noSpaces)
      },
      Method.DELETE / "api" / "expense-list" / int("expenseListId") -> handler { (expenseListId: Int, request: Request) =>
        for {
          response <- ExpenseListDeleteHandler(expenseListId)
        } yield Response.json(response.noSpaces)
      },
      Method.GET / "api" / "expense-list" / int("expenseListId") -> handler { (expenseListId: Int, request: Request) =>
        for {
          response <- ExpenseListGetHandler(expenseListId)
        } yield Response.json(response.noSpaces)
      }
    )
}
