package dawidkobak.myfunds.stockanalysis.handler

import dawidkobak.myfunds.domain.DatabaseAccess
import doobie.implicits.*
import io.circe.*
import io.circe.parser.*
import zio.*
import zio.interop.catz.*

object StockAnalysisGetHandler {
  def apply(stockAnalysisId: Int): RIO[DatabaseAccess, Json] =
    for {
      databaseAccess <- ZIO.service[DatabaseAccess]
      stockAnalysis <-
        sql"""
              SELECT
                mf_stock_analysis.id,
                mf_stock_analysis.stock_id,
                mf_stock.name,
                mf_stock_analysis.company_name,
                mf_stock_analysis.ticker,
                mf_stock_analysis.country,
                mf_stock_analysis.industry,
                mf_stock_analysis.source_markdown,
                mf_stock_analysis.source_file_path,
                mf_stock_analysis.status,
                mf_stock_analysis.model,
                mf_stock_analysis.prompt_version,
                mf_stock_analysis.report_markdown,
                mf_stock_analysis.total_score,
                mf_stock_analysis.decision,
                mf_stock_analysis.metadata_json::text,
                mf_stock_analysis.error_message,
                mf_stock_analysis.created_at,
                mf_stock_analysis.processing_started_at,
                mf_stock_analysis.completed_at,
                mf_stock_analysis.updated_at
              FROM mf_stock_analysis
              LEFT JOIN mf_stock ON mf_stock_analysis.stock_id = mf_stock.id
              WHERE mf_stock_analysis.id = $stockAnalysisId
           """
          .query[
            (
              Int,
              Option[Int],
              Option[String],
              String,
              Option[String],
              Option[String],
              Option[String],
              String,
              Option[String],
              String,
              String,
              String,
              Option[String],
              Option[Int],
              Option[String],
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
            stockId,
            stockName,
            companyName,
            ticker,
            country,
            industry,
            sourceMarkdown,
            sourceFilePath,
            status,
            model,
            promptVersion,
            reportMarkdown,
            totalScore,
            decision,
            metadataJson,
            errorMessage,
            createdAt,
            processingStartedAt,
            completedAt,
            updatedAt
          ) =>
        Json.obj(
          "id" -> Json.fromInt(stockAnalysisId),
          "stock" -> stockId.fold(Json.Null)(stockId =>
            Json.obj(
              "id"   -> Json.fromInt(stockId),
              "name" -> stockName.fold(Json.Null)(Json.fromString)
            )
          ),
          "companyName"         -> Json.fromString(companyName),
          "ticker"              -> ticker.fold(Json.Null)(Json.fromString),
          "country"             -> country.fold(Json.Null)(Json.fromString),
          "industry"            -> industry.fold(Json.Null)(Json.fromString),
          "sourceMarkdown"      -> Json.fromString(sourceMarkdown),
          "sourceFilePath"      -> sourceFilePath.fold(Json.Null)(Json.fromString),
          "status"              -> Json.fromString(status),
          "model"               -> Json.fromString(model),
          "promptVersion"       -> Json.fromString(promptVersion),
          "reportMarkdown"      -> reportMarkdown.fold(Json.Null)(Json.fromString),
          "totalScore"          -> totalScore.fold(Json.Null)(Json.fromInt),
          "decision"            -> decision.fold(Json.Null)(Json.fromString),
          "metadataJson"        -> metadataJson.flatMap(parse(_).toOption).getOrElse(Json.Null),
          "errorMessage"        -> errorMessage.fold(Json.Null)(Json.fromString),
          "createdAt"           -> Json.fromString(createdAt),
          "processingStartedAt" -> processingStartedAt.fold(Json.Null)(Json.fromString),
          "completedAt"         -> completedAt.fold(Json.Null)(Json.fromString),
          "updatedAt"           -> Json.fromString(updatedAt)
        )
    }
}
