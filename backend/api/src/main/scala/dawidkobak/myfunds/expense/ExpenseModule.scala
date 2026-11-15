package dawidkobak.myfunds.expense

import sttp.client3.httpclient.zio.{HttpClientZioBackend, SttpClient}
import dawidkobak.myfunds.domain.DatabaseAccess
import dawidkobak.myfunds.domain.helper.*
import dawidkobak.myfunds.domain.helper.RequestJsonBody.*
import dawidkobak.myfunds.expense.handler.{ExpenseCreateHandler, ExpenseDeleteHandler, ExpenseListHandler, ExpenseUpdateHandler}
import zio.*
import zio.http.*

object ExpenseModule {
  def httpApp: Routes[DatabaseAccess, Throwable] =
    Routes(
      Method.GET / "api" / "expense" / "list" -> handler { (request: Request) =>
        for {
          username <- ZIO.succeed("demo")
          response <- ExpenseListHandler(username)
        } yield Response.json(response.noSpaces)
      },
      Method.POST / "api" / "expense" / "create" -> handler { (request: Request) =>
        for {
          payload  <- request.readJson
          owner    <- ZIO.succeed("demo")
          response <- ExpenseCreateHandler(payload, owner)
        } yield Response.json(response.noSpaces)
      },
      Method.PATCH / "api" / "expense" / "update" -> handler { (request: Request) =>
        for {
          payload  <- request.readJson
          owner    <- ZIO.succeed("demo")
          response <- ExpenseUpdateHandler(payload, owner)
        } yield Response.json(response.noSpaces)
      },
      Method.DELETE / "api" / "expense" / string("expenseId") -> handler { (expenseId: String, request: Request) =>
        for {
          id       <- ZIO.attempt(expenseId.toInt)
          response <- ExpenseDeleteHandler(id)
        } yield Response.json(response.noSpaces)
      }
    )
}
