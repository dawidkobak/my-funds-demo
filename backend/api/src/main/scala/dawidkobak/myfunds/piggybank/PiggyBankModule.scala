package dawidkobak.myfunds.piggybank

import sttp.client3.httpclient.zio.{HttpClientZioBackend, SttpClient}
import dawidkobak.myfunds.domain.DatabaseAccess
import dawidkobak.myfunds.domain.helper.*
import dawidkobak.myfunds.domain.helper.RequestJsonBody.*
import dawidkobak.myfunds.piggybank.handler.{PiggyBankCreateHandler, PiggyBankDeleteHandler, PiggyBankListHandler, PiggyBankUpdateHandler}
import zio.*
import zio.http.*

object PiggyBankModule {
  def httpApp: Routes[DatabaseAccess, Throwable] =
    Routes(
      Method.GET / "api" / "piggy-bank" / "list" -> handler { (request: Request) =>
        for {
          response <- PiggyBankListHandler.apply
        } yield Response.json(response.noSpaces)
      },
      Method.POST / "api" / "piggy-bank" / "create" -> handler { (request: Request) =>
        for {
          payload  <- request.readJson
          owner    <- ZIO.succeed("demo")
          response <- PiggyBankCreateHandler(payload, owner)
        } yield Response.json(response.noSpaces)
      },
      Method.PATCH / "api" / "piggy-bank" / "update" -> handler { (request: Request) =>
        for {
          payload  <- request.readJson
          owner    <- ZIO.succeed("demo")
          response <- PiggyBankUpdateHandler(payload, owner)
        } yield Response.json(response.noSpaces)
      },
      Method.DELETE / "api" / "piggy-bank" / string("piggyBankId") -> handler { (piggyBankId: String, request: Request) =>
        for {
          id       <- ZIO.attempt(piggyBankId.toInt)
          response <- PiggyBankDeleteHandler(id)
        } yield Response.json(response.noSpaces)
      }
    )
}
