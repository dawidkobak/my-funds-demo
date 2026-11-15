package dawidkobak

import dawidkobak.myfunds.migrations.FlywayMigrationsModule
import java.lang.System
import java.time.Clock

import com.typesafe.config.{Config, ConfigFactory}
import dawidkobak.myfunds.bond.BondModule
import dawidkobak.myfunds.transaction.TransactionModule
import dawidkobak.myfunds.domain.DatabaseAccess
import dawidkobak.myfunds.domain.helper.*
import dawidkobak.myfunds.expenses.ExpenseModule
import dawidkobak.myfunds.piggybank.PiggyBankModule
import dawidkobak.myfunds.stock.StockModule
import dawidkobak.myfunds.stockanalysis.StockAnalysisModule
import dawidkobak.myfunds.stockquote.StockQuoteModule
import dawidkobak.myfunds.wallet.WalletModule
import zio.*
import zio.logging.backend.SLF4J
import zio.http.*
import zio.interop.catz.*
import doobie.implicits.*
import sttp.client3.httpclient.zio.SttpClient
import zio.http.Server.RequestStreaming

object Application extends ZIOAppDefault {
  override val bootstrap: ZLayer[ZIOAppArgs, Any, Any] =
    Runtime.removeDefaultLoggers >>> SLF4J.slf4j

  val environment: TaskLayer[Config & Scope] = ZLayer.succeed {
    ConfigFactory.load
  } ++ Scope.default

  override val run: Task[ExitCode] =
    FlywayMigrationsModule.migrate *> ZIO.blockingExecutor.flatMap { executor =>
      val resources =
        for {
          databaseAccess <- DatabaseAccess.openTransactor(executor.asExecutionContext)
        } yield databaseAccess

      resources.use { databaseAccess =>
        val healthcheckEndpoint = Routes(Method.GET / "api" / "healthcheck" -> Handler.ok)

        // Define modules
        val piggyBankModule     = PiggyBankModule.httpApp
        val expenseModule       = ExpenseModule.httpApp
        val stockModule         = StockModule.httpApp
        val stockAnalysisModule = StockAnalysisModule.httpApp
        val walletModule        = WalletModule.httpApp
        val stockQuoteModule    = StockQuoteModule.httpApp
        val transactionModule   = TransactionModule.httpApp
        val bondModule          = BondModule.httpApp

        // Define server
        val httpServerConfig =
          Server.Config.default
            .port(8088)
            .maxHeaderSize(32768)
            .copy(requestStreaming = RequestStreaming.Disabled(Integer.MAX_VALUE))

        val allModules = (
          healthcheckEndpoint ++
            piggyBankModule ++
            expenseModule ++
            stockModule ++
            stockAnalysisModule ++
            walletModule ++
            stockQuoteModule ++
            transactionModule ++
            bondModule
        ).handleErrorRequestCauseZIO(HttpFailureResponse.respond)

        ZIO
          .foreachDiscard(
            List(
              sql"SELECT 1".query[Int].unique.transact(databaseAccess)
            )
          )(_.forkDaemon) *>
          Server
            .serve(allModules)
            .provide(
              environment,
              ZLayer.succeed(httpServerConfig),
              ZLayer.succeed(DatabaseAccess(databaseAccess)),
              Server.live
            )
            .exitCode
      }
    }
}
