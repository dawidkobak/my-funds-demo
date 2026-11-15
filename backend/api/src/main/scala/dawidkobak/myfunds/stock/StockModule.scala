package dawidkobak.myfunds.stock

import dawidkobak.myfunds.domain.DatabaseAccess
import dawidkobak.myfunds.domain.helper.*
import dawidkobak.myfunds.domain.helper.RequestJsonBody.*
import dawidkobak.myfunds.stock.handler.{StockCreateHandler, StockDeleteHandler, StockListHandler, StockUpdateHandler}
import sttp.client3.httpclient.zio.{HttpClientZioBackend, SttpClient}
import zio.*
import zio.http.*

object StockModule {
  def httpApp: Routes[DatabaseAccess, Throwable] =
    Routes(
      Method.GET / "api" / "stock" / "list" -> handler { (request: Request) =>
        for {
          response <- StockListHandler.apply
        } yield Response.json(response.noSpaces)
      },
      Method.POST / "api" / "stock" / "create" -> handler { (request: Request) =>
        for {
          payload  <- request.readJson
          response <- StockCreateHandler(payload)
        } yield Response.json(response.noSpaces)
      },
      Method.PATCH / "api" / "stock" / "update" -> handler { (request: Request) =>
        for {
          payload  <- request.readJson
          response <- StockUpdateHandler(payload)
        } yield Response.json(response.noSpaces)
      },
      Method.DELETE / "api" / "stock" / string("stockId") -> handler { (stockId: String, request: Request) =>
        for {
          id       <- ZIO.attempt(stockId.toInt)
          response <- StockDeleteHandler(id)
        } yield Response.json(response.noSpaces)
      }
    )
}
