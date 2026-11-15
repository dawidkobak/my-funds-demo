package dawidkobak.myfunds.subexpense

import dawidkobak.myfunds.domain.DatabaseAccess
import dawidkobak.myfunds.domain.helper.*
import dawidkobak.myfunds.domain.helper.RequestJsonBody.*
import dawidkobak.myfunds.subexpense.handler.{SubExpenseCreateHandler, SubExpenseDeleteHandler, SubExpenseListHandler, SubExpenseUpdateHandler}
import sttp.client3.httpclient.zio.{HttpClientZioBackend, SttpClient}
import zio.*
import zio.http.*

object SubExpenseModule {
  def httpApp: Routes[DatabaseAccess, Throwable] =
    Routes(
      Method.GET / "api" / "sub-expense" / "list" -> handler { (request: Request) =>
        for {
          response <- SubExpenseListHandler.apply
        } yield Response.json(response.noSpaces)
      },
      Method.POST / "api" / "sub-expense" / "create" -> handler { (request: Request) =>
        for {
          payload  <- request.readJson
          owner    <- ZIO.succeed("test")
          response <- SubExpenseCreateHandler(payload, owner)
        } yield Response.json(response.noSpaces)
      },
      Method.PATCH / "api" / "sub-expense" / "update" -> handler { (request: Request) =>
        for {
          payload  <- request.readJson
          owner    <- ZIO.succeed("test")
          response <- SubExpenseUpdateHandler(payload, owner)
        } yield Response.json(response.noSpaces)
      },
      Method.DELETE / "api" / "sub-expense" / string("subExpenseId") -> handler { (subExpenseId: String, request: Request) =>
        for {
          id       <- ZIO.attempt(subExpenseId.toInt)
          response <- SubExpenseDeleteHandler(id)
        } yield Response.json(response.noSpaces)
      }
    )
}
