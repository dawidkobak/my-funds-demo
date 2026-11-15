package dawidkobak.myfunds.cli

import java.security.Security

import dawidkobak.myfunds.cli.helper.ErrorUtils
import dawidkobak.myfunds.cli.model.*
import dawidkobak.myfunds.cli.currency.ImportCurrencyRatesCommand
import dawidkobak.myfunds.cli.stock.ImportYahooNbpQuotesCommand
import dawidkobak.myfunds.domain.DatabaseAccess
import zio.*
import zio.cli.*
import zio.cli.HelpDoc.*
import zio.logging.backend.SLF4J

object CliApplication extends ZIOApp {
  override val bootstrap: ZLayer[ZIOAppArgs, Any, DatabaseAccess & ZIOAppArgs] =
    (Runtime.removeDefaultLoggers >>> SLF4J.slf4j) ++
      DatabaseAccess.layer ++
      ZLayer.environment[ZIOAppArgs]

  override type Environment = DatabaseAccess & ZIOAppArgs
  override val environmentTag = EnvironmentTag[Environment]

  private[cli] val commandTree: CommandBranch = CommandBranch(
    "",
    List(
      CommandBranch(
        "stock",
        List(
          CommandLeaf(ImportYahooNbpQuotesCommand)
        )
      ),
      CommandBranch(
        "currency",
        List(
          CommandLeaf(ImportCurrencyRatesCommand)
        )
      )
    )
  )

  def run: RIO[Environment, Any] =
    for {
      _ <- ZIO.attempt {
        Security.setProperty("networkaddress.cache.ttl", "60")
        Security.setProperty("networkaddress.cache.negative.ttl", "0")
      }
      cliArguments <- ZIOAppArgs.getArgs
      commandExecutionResult <-
        CliApp
          .make(
            name = "My Funds CLI",
            version = "1.0",
            command = CommandTree.buildParser(commandTree),
            summary = Span.text("My Funds CLI"),
            config = CliConfig.default.copy(finalCheckBuiltIn = false)
          )(
            CommandTree
              .executionHandlers(cliArguments, commandTree)
              .foldLeft(PartialFunction.empty[CliAction, RIO[Environment, Unit]])(_ orElse _)
          )
          .run(cliArguments.toList)
          .tapError(error => ErrorUtils.cliAppErrorHandler(cliArguments.toList)(error))
    } yield commandExecutionResult
}
