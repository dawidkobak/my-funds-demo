package dawidkobak.myfunds.wallet

import dawidkobak.myfunds.domain.DatabaseAccess
import dawidkobak.myfunds.domain.helper.*
import dawidkobak.myfunds.domain.helper.RequestJsonBody.*
import dawidkobak.myfunds.wallet.handler.{
  NetWorthSummaryHandler,
  NetWorthValueInTimeHandler,
  WalletCreateHandler,
  WalletDeleteHandler,
  WalletListHandler,
  WalletUpdateHandler,
  WalletValueInTimeHandler
}
import sttp.client3.httpclient.zio.{HttpClientZioBackend, SttpClient}
import zio.*
import zio.http.*

object WalletModule {
  def httpApp: Routes[DatabaseAccess, Throwable] =
    Routes(
      Method.GET / "api" / "wallet" / "list" -> handler { (request: Request) =>
        for {
          walletType <- ZIO.attempt(request.url.queryParams.queryParams("wallet.type").headOption)
          response   <- WalletListHandler.apply(walletType)
        } yield Response.json(response.noSpaces)
      },
      Method.POST / "api" / "wallet" / "create" -> handler { (request: Request) =>
        for {
          payload  <- request.readJson
          owner    <- ZIO.succeed("demo")
          response <- WalletCreateHandler(payload, owner)
        } yield Response.json(response.noSpaces)
      },
      Method.PATCH / "api" / "wallet" / "update" -> handler { (request: Request) =>
        for {
          payload  <- request.readJson
          owner    <- ZIO.succeed("demo")
          response <- WalletUpdateHandler(payload, owner)
        } yield Response.json(response.noSpaces)
      },
      Method.GET / "api" / "wallet" / "net-worth" -> handler { (request: Request) =>
        for {
          valuationDate <- ZIO.fromOption(request.url.queryParams.queryParams("date").headOption).orElseFail(new IllegalArgumentException("Missing date query parameter"))
          response      <- NetWorthSummaryHandler(valuationDate)
        } yield Response.json(response.noSpaces)
      },
      Method.GET / "api" / "wallet" / "net-worth" / "value-in-time" -> handler { (request: Request) =>
        for {
          response <- NetWorthValueInTimeHandler.apply
        } yield Response.json(response.noSpaces)
      },
      Method.GET / "api" / "wallet" / int("walletId") / "value-in-time" -> handler { (walletId: Int, request: Request) =>
        for {
          response <- WalletValueInTimeHandler(walletId)
        } yield Response.json(response.noSpaces)
      },
      Method.DELETE / "api" / "wallet" / int("walletId") -> handler { (walletId: Int, request: Request) =>
        for {
          response <- WalletDeleteHandler(walletId)
        } yield Response.json(response.noSpaces)
      }
    )
}
