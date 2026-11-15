package dawidkobak.myfunds.bond

import dawidkobak.myfunds.bond.handler.{BondIssueListHandler, BondPositionCreateHandler, BondPositionDeleteHandler, BondPositionListHandler, BondPositionUpdateHandler, BondWalletValueInTimeHandler}
import dawidkobak.myfunds.domain.DatabaseAccess
import dawidkobak.myfunds.domain.helper.*
import dawidkobak.myfunds.domain.helper.RequestJsonBody.*
import zio.*
import zio.http.*

object BondModule {
  def httpApp: Routes[DatabaseAccess, Throwable] =
    Routes(
      Method.GET / "api" / "bond" / "issue" / "list" -> handler { (request: Request) =>
        for {
          bondType <- ZIO.attempt(request.url.queryParams.queryParams("bondType").headOption)
          response <- BondIssueListHandler(bondType)
        } yield Response.json(response.noSpaces)
      },
      Method.GET / "api" / "bond" / "position" / "list" -> handler { (request: Request) =>
        for {
          walletId      <- ZIO.attempt(request.url.queryParams.queryParams("wallet.id").headOption.map(_.toInt))
          valuationDate <- ZIO.attempt(request.url.queryParams.queryParams("date").headOption)
          response      <- BondPositionListHandler(walletId, valuationDate)
        } yield Response.json(response.noSpaces)
      },
      Method.POST / "api" / "bond" / "position" -> handler { (request: Request) =>
        for {
          payload  <- request.readJson
          response <- BondPositionCreateHandler(payload)
        } yield Response.json(response.noSpaces)
      },
      Method.PATCH / "api" / "bond" / "position" -> handler { (request: Request) =>
        for {
          payload  <- request.readJson
          response <- BondPositionUpdateHandler(payload)
        } yield Response.json(response.noSpaces)
      },
      Method.DELETE / "api" / "bond" / "position" / int("bondPositionId") -> handler { (bondPositionId: Int, request: Request) =>
        for {
          response <- BondPositionDeleteHandler(bondPositionId)
        } yield Response.json(response.noSpaces)
      },
      Method.GET / "api" / "bond" / "wallet" / int("walletId") / "value-in-time" -> handler { (walletId: Int, request: Request) =>
        for {
          response <- BondWalletValueInTimeHandler(walletId)
        } yield Response.json(response.noSpaces)
      }
    )
}
