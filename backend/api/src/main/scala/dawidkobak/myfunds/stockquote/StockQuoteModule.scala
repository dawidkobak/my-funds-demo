package dawidkobak.myfunds.stockquote

import dawidkobak.myfunds.domain.DatabaseAccess
import dawidkobak.myfunds.domain.helper.*
import dawidkobak.myfunds.domain.helper.RequestJsonBody.*
import dawidkobak.myfunds.stockquote.handler.{StockQuoteCreateHandler, StockQuoteDeleteHandler, StockQuoteGetHandler, StockQuoteListHandler, StockQuoteUpdateHandler}
import sttp.client3.httpclient.zio.{HttpClientZioBackend, SttpClient}
import zio.*
import zio.http.*

object StockQuoteModule {
  def httpApp: Routes[DatabaseAccess, Throwable] =
    Routes(
      Method.GET / "api" / "stock-quote" / "list" -> handler { (request: Request) =>
        for {
          response <- StockQuoteListHandler.apply
        } yield Response.json(response.noSpaces)
      },
      Method.POST / "api" / "stock-quote" / "create" -> handler { (request: Request) =>
        for {
          payload  <- request.readJson
          response <- StockQuoteCreateHandler(payload)
        } yield Response.json(response.noSpaces)
      },
      Method.PATCH / "api" / "stock-quote" / "update" -> handler { (request: Request) =>
        for {
          payload  <- request.readJson
          response <- StockQuoteUpdateHandler(payload)
        } yield Response.json(response.noSpaces)
      },
      Method.DELETE / "api" / "stock-quote" / int("stockQuoteId") -> handler { (stockQuoteId: Int, request: Request) =>
        for {
          response <- StockQuoteDeleteHandler(stockQuoteId)
        } yield Response.json(response.noSpaces)
      },
      Method.GET / "api" / "stock-quote" / string("stockQuoteDate") -> handler { (stockQuoteDate: String, request: Request) =>
        for {
          response <- StockQuoteGetHandler(stockQuoteDate)
        } yield Response.json(response.noSpaces)
      }
    )
}
