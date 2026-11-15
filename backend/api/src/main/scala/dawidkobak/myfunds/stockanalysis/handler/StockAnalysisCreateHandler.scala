package dawidkobak.myfunds.stockanalysis.handler

import dawidkobak.myfunds.domain.DatabaseAccess
import doobie.implicits.*
import io.circe.*
import zio.*
import zio.interop.catz.*

object StockAnalysisCreateHandler {
  def apply(payload: Json): RIO[DatabaseAccess, Json] =
    for {
      databaseAccess <- ZIO.service[DatabaseAccess]
      stockId          <- ZIO.fromEither(payload.hcursor.downField("stock").get[Int]("id")).option
      companyName      <- ZIO.fromEither(payload.hcursor.get[String]("companyName"))
      ticker           <- ZIO.fromEither(payload.hcursor.get[Option[String]]("ticker"))
      country          <- ZIO.fromEither(payload.hcursor.get[Option[String]]("country"))
      industry         <- ZIO.fromEither(payload.hcursor.get[Option[String]]("industry"))
      sourceMarkdown   <- ZIO.fromEither(payload.hcursor.get[String]("sourceMarkdown"))
      stockAnalysis <-
        sql"""
              INSERT INTO mf_stock_analysis (
                stock_id,
                company_name,
                ticker,
                country,
                industry,
                source_markdown,
                source_file_path,
                status,
                model,
                prompt_version,
                created_at,
                updated_at
              )
              VALUES (
                $stockId,
                $companyName,
                $ticker,
                $country,
                $industry,
                $sourceMarkdown,
                NULL,
                'PENDING',
                'gpt-5.4',
                'stock-analysis-v2',
                NOW(),
                NOW()
              )
              RETURNING
                mf_stock_analysis.id,
                mf_stock_analysis.stock_id,
                mf_stock_analysis.company_name,
                mf_stock_analysis.ticker,
                mf_stock_analysis.country,
                mf_stock_analysis.industry,
                mf_stock_analysis.status,
                mf_stock_analysis.model,
                mf_stock_analysis.prompt_version,
                mf_stock_analysis.total_score,
                mf_stock_analysis.decision,
                mf_stock_analysis.error_message,
                mf_stock_analysis.created_at,
                mf_stock_analysis.processing_started_at,
                mf_stock_analysis.completed_at,
                mf_stock_analysis.updated_at
           """
          .query[
            (
              Int,
              Option[Int],
              String,
              Option[String],
              Option[String],
              Option[String],
              String,
              String,
              String,
              Option[Int],
              Option[String],
              Option[String],
              String,
              Option[String],
              Option[String],
              String
            )
          ]
          .unique
          .transact(databaseAccess.transactor)
    } yield stockAnalysis match {
      case (
            stockAnalysisId,
            returnedStockId,
            returnedCompanyName,
            returnedTicker,
            returnedCountry,
            returnedIndustry,
            status,
            model,
            promptVersion,
            totalScore,
            decision,
            errorMessage,
            createdAt,
            processingStartedAt,
            completedAt,
            updatedAt
          ) =>
        Json.obj(
          "id" -> Json.fromInt(stockAnalysisId),
          "stock" -> returnedStockId.fold(Json.Null)(returnedStockId =>
            Json.obj(
              "id" -> Json.fromInt(returnedStockId)
            )
          ),
          "companyName"         -> Json.fromString(returnedCompanyName),
          "ticker"              -> returnedTicker.fold(Json.Null)(Json.fromString),
          "country"             -> returnedCountry.fold(Json.Null)(Json.fromString),
          "industry"            -> returnedIndustry.fold(Json.Null)(Json.fromString),
          "status"              -> Json.fromString(status),
          "model"               -> Json.fromString(model),
          "promptVersion"       -> Json.fromString(promptVersion),
          "totalScore"          -> totalScore.fold(Json.Null)(Json.fromInt),
          "decision"            -> decision.fold(Json.Null)(Json.fromString),
          "errorMessage"        -> errorMessage.fold(Json.Null)(Json.fromString),
          "createdAt"           -> Json.fromString(createdAt),
          "processingStartedAt" -> processingStartedAt.fold(Json.Null)(Json.fromString),
          "completedAt"         -> completedAt.fold(Json.Null)(Json.fromString),
          "updatedAt"           -> Json.fromString(updatedAt)
        )
    }
}
