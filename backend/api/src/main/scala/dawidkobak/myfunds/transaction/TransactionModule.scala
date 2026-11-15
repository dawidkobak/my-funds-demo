package dawidkobak.myfunds.transaction

import dawidkobak.myfunds.transaction.handler.{TransactionCreateHandler, TransactionDeleteHandler, TransactionListHandler, TransactionUpdateHandler}
import dawidkobak.myfunds.domain.DatabaseAccess
import dawidkobak.myfunds.domain.helper.*
import dawidkobak.myfunds.domain.helper.RequestJsonBody.*
import sttp.client3.httpclient.zio.{HttpClientZioBackend, SttpClient}
import zio.*
import zio.http.*

object TransactionModule {
  def httpApp: Routes[DatabaseAccess, Throwable] =
    Routes(
      Method.GET / "api" / "transaction" / "list" -> handler { (request: Request) =>
        for {
          walletId <- ZIO.succeed(request.url.queryParams.queryParams("wallet.id").headOption.map(_.toInt))
          response <- TransactionListHandler.apply(walletId)
        } yield Response.json(response.noSpaces)
      },
      Method.POST / "api" / "transaction" / "create" -> handler { (request: Request) =>
        for {
          payload  <- request.readJson
          owner    <- ZIO.succeed("demo")
          response <- TransactionCreateHandler(payload, owner)
        } yield Response.json(response.noSpaces)
      },
      Method.PATCH / "api" / "transaction" / "update" -> handler { (request: Request) =>
        for {
          payload  <- request.readJson
          owner    <- ZIO.succeed("demo")
          response <- TransactionUpdateHandler(payload, owner)
        } yield Response.json(response.noSpaces)
      },
      Method.DELETE / "api" / "transaction" / string("transactionId") -> handler { (transactionId: String, request: Request) =>
        for {
          id       <- ZIO.attempt(transactionId.toInt)
          response <- TransactionDeleteHandler(id)
        } yield Response.json(response.noSpaces)
      }
    )
}
