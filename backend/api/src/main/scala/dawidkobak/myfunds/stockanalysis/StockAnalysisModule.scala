package dawidkobak.myfunds.stockanalysis

import dawidkobak.myfunds.domain.DatabaseAccess
import dawidkobak.myfunds.domain.helper.RequestJsonBody.*
import dawidkobak.myfunds.stockanalysis.handler.{StockAnalysisCreateHandler, StockAnalysisGetHandler, StockAnalysisListHandler}
import zio.*
import zio.http.*

object StockAnalysisModule {
  def httpApp: Routes[DatabaseAccess, Throwable] =
    Routes(
      Method.GET / "api" / "stock-analysis" / "list" -> handler { (request: Request) =>
        for {
          response <- StockAnalysisListHandler.apply
        } yield Response.json(response.noSpaces)
      },
      Method.POST / "api" / "stock-analysis" / "create" -> handler { (request: Request) =>
        for {
          payload  <- request.readJson
          response <- StockAnalysisCreateHandler(payload)
        } yield Response.json(response.noSpaces)
      },
      Method.GET / "api" / "stock-analysis" / int("stockAnalysisId") -> handler { (stockAnalysisId: Int, request: Request) =>
        for {
          response <- StockAnalysisGetHandler(stockAnalysisId)
        } yield Response.json(response.noSpaces)
      }
    )
}
