package dawidkobak.myfunds.cli.stock

import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path, StandardCopyOption}
import java.time.{LocalDate, ZoneId, ZoneOffset}
import java.util.UUID

import cats.syntax.all.*
import dawidkobak.myfunds.cli.CliApplication.Environment
import dawidkobak.myfunds.cli.model.*
import dawidkobak.myfunds.domain.DatabaseAccess
import dawidkobak.myfunds.implicits.SttpClientImplicits.*
import doobie.ConnectionIO
import doobie.free.connection
import doobie.implicits.*
import io.circe.Json
import io.circe.parser.parse
import sttp.client3.*
import sttp.client3.httpclient.zio.{HttpClientZioBackend, SttpClient}
import zio.*
import zio.cli.{Args, Command, Options}
import zio.cli.HelpDoc.p
import zio.interop.catz.*

private final case class ImportYahooNbpQuotes(date: Option[String], dryRun: Boolean, reportDirectory: String) extends CliAction

object ImportYahooNbpQuotesCommand extends RunnableCommand[ImportYahooNbpQuotes] {
  def apply(dateCliArgument: Option[String], dryRun: Boolean, reportDirectory: String): RIO[DatabaseAccess & SttpClient, Unit] =
    for {
      databaseAccess <- ZIO.service[DatabaseAccess]
      httpClient       <- ZIO.service[SttpClient]
      startedAt        <- Clock.instant
      today = startedAt.atZone(ZoneId.of("Europe/Warsaw")).toLocalDate
      requestedDate       <- ZIO.attempt(dateCliArgument.map(LocalDate.parse).getOrElse(today))
      _                   <- ZIO.fail(new IllegalArgumentException(s"Cannot import future market data for $requestedDate")).when(requestedDate.isAfter(today))
      reportDirectoryPath <- ZIO.attemptBlocking(Files.createDirectories(Path.of(reportDirectory).toAbsolutePath))
      stocks <-
        sql"""
          SELECT mf_stock.id, mf_stock.name, mf_stock.stooqname, mf_currency.code
          FROM mf_stock
          LEFT JOIN mf_currency ON mf_currency.id = mf_stock.currency_id
          WHERE mf_stock.stooqname IS NOT NULL AND EXISTS (
            SELECT 1 FROM mf_transaction
            WHERE mf_transaction.stock_id = mf_stock.id
              AND mf_transaction.purchasedate <= ${requestedDate.toString}::date
            GROUP BY mf_transaction.wallet_id
            HAVING SUM(CASE WHEN mf_transaction.transactiontype = 'SELL' THEN -mf_transaction.amount ELSE mf_transaction.amount END) > 0
          )
          ORDER BY mf_stock.id
        """.query[(Int, String, Option[String], Option[String])].to[List].transact(databaseAccess.transactor)
      _ <- ZIO.fail(new IllegalArgumentException(s"No held instruments to import on $requestedDate")).when(stocks.isEmpty)
      mappedStocks <- ZIO.foreach(stocks) { case (stockIdentifier, stockName, stooqSymbol, currencyCode) =>
        for {
          sourceSymbol <- ZIO
            .fromOption(stooqSymbol.filter(_.trim.nonEmpty))
            .orElseFail(new IllegalArgumentException(s"Missing source symbol for held instrument $stockName"))
          storedCurrency <- ZIO
            .fromOption(currencyCode)
            .orElseFail(new IllegalArgumentException(s"Missing currency for held instrument $stockName"))
          yahooSymbol <- ZIO.attempt(YahooNbpImportHelper.yahooSymbol(sourceSymbol))
        } yield (stockIdentifier, stockName, sourceSymbol, storedCurrency, yahooSymbol)
      }
      currencies <- sql"SELECT mf_currency.id, mf_currency.code FROM mf_currency WHERE mf_currency.code <> 'PLN' ORDER BY mf_currency.id"
        .query[(Int, String)]
        .to[List]
        .transact(databaseAccess.transactor)
      requiredCurrencies = currencies.map(_._2).toSet
      _ <- ZIO
        .fail(new IllegalArgumentException("Missing configured NBP currency for a held instrument"))
        .unless(mappedStocks.forall { case (_, _, _, storedCurrency, yahooSymbol) =>
          (storedCurrency == "PLN" || requiredCurrencies.contains(storedCurrency)) && (yahooSymbol != "BTC-USD" || requiredCurrencies.contains("USD"))
        })
      historyStartDate     = requestedDate.minusDays(14)
      periodStartTimestamp = historyStartDate.minusDays(1).atStartOfDay(ZoneOffset.UTC).toEpochSecond
      periodEndTimestamp   = requestedDate.plusDays(1).atStartOfDay(ZoneOffset.UTC).toEpochSecond
      _ <- ZIO.logInfo(s"Downloading Yahoo quotes for ${mappedStocks.size} held instruments and NBP rates on or before $requestedDate; dry-run=$dryRun")
      nbpResponse <-
        (for {
          response <- httpClient.send(
            basicRequest
              .get(uri"https://api.nbp.pl/api/exchangerates/tables/A/$historyStartDate/$requestedDate/?format=json")
              .readTimeout(scala.concurrent.duration.Duration(30, "seconds"))
              .response(asStringAlways)
          )
          _ <- ZIO.fail(MarketDataHttpException("NBP", response.code.code)).unless(response.code.isSuccess)
        } yield response).retry(
          Schedule.exponential(1.second) && Schedule.recurs(2) && Schedule.recurWhile[Throwable] {
            case error: MarketDataHttpException => error.statusCode == 429 || error.statusCode >= 500
            case _: java.io.IOException         => true
            case _                              => false
          }
        )
      currencyRatesByCurrency <- ZIO.attempt(YahooNbpImportHelper.nbpRates(nbpResponse.body, requiredCurrencies, requestedDate))
      stockHistories <- ZIO.foreach(mappedStocks) { case (stockIdentifier, stockName, stooqSymbol, storedCurrency, yahooSymbol) =>
        for {
          sourceUrl <- ZIO.attempt(uri"https://query1.finance.yahoo.com/v8/finance/chart/$yahooSymbol?interval=1d&period1=$periodStartTimestamp&period2=$periodEndTimestamp")
          response <-
            (for {
              response <- httpClient.send(
                basicRequest
                  .header("User-Agent", "Mozilla/5.0")
                  .header("Accept", "application/json")
                  .get(sourceUrl)
                  .readTimeout(scala.concurrent.duration.Duration(30, "seconds"))
                  .response(asStringAlways)
              )
              _ <- ZIO.fail(MarketDataHttpException(s"Yahoo $yahooSymbol", response.code.code)).unless(response.code.isSuccess)
            } yield response).retry(
              Schedule.exponential(1.second) && Schedule.recurs(2) && Schedule.recurWhile[Throwable] {
                case error: MarketDataHttpException => error.statusCode == 429 || error.statusCode >= 500
                case _: java.io.IOException         => true
                case _                              => false
              }
            )
          observedAt   <- Clock.instant
          quoteHistory <- ZIO.attempt(YahooNbpImportHelper.yahooQuotes(response.body, yahooSymbol, storedCurrency, requestedDate, observedAt))
          sourceJson   <- ZIO.fromEither(parse(response.body))
          _            <- ZIO.sleep(500.millis)
        } yield (stockIdentifier, stockName, stooqSymbol, storedCurrency, quoteHistory, sourceUrl.toString, observedAt, sourceJson)
      }
      selectedDate <- ZIO.attempt(
        YahooNbpImportHelper.commonDate(
          requestedDate,
          stockHistories.map(_._5.valuesByDate.keySet) ++ currencyRatesByCurrency.values.map(_.keySet).toList
        )
      )
      selectedCurrencyRates = currencies.map { case (currencyIdentifier, currencyCode) =>
        (currencyIdentifier, currencyCode, currencyRatesByCurrency(currencyCode)(selectedDate).setScale(8, BigDecimal.RoundingMode.HALF_UP))
      }
      selectedStockQuotes = stockHistories.map { case (stockIdentifier, stockName, stooqSymbol, storedCurrency, quoteHistory, sourceUrl, observedAt, sourceJson) =>
        (
          stockIdentifier,
          (
            if (quoteHistory.currency == "USD" && storedCurrency == "PLN") quoteHistory.valuesByDate(selectedDate) * currencyRatesByCurrency("USD")(selectedDate)
            else quoteHistory.valuesByDate(selectedDate)
          ).setScale(16, BigDecimal.RoundingMode.HALF_UP)
        )
      }
      timestamp           = s"$selectedDate 00:00:00"
      reportPath          = reportDirectoryPath.resolve(s"$requestedDate-${UUID.randomUUID()}.json")
      temporaryReportPath = reportPath.resolveSibling(s"${reportPath.getFileName}.tmp")
      report = Json.obj(
        "requestedDate" -> Json.fromString(requestedDate.toString),
        "storedDate"    -> Json.fromString(selectedDate.toString),
        "startedAt"     -> Json.fromString(startedAt.toString),
        "dryRun"        -> Json.fromBoolean(dryRun),
        "status"        -> Json.fromString(if (dryRun) "dry-run" else "prepared"),
        "fxConvention"  -> Json.fromString("NBP table A mid"),
        "nbpResponse"   -> parse(nbpResponse.body).fold(throw _, identity),
        "currencyRates" -> Json.fromValues(selectedCurrencyRates.map { case (currencyIdentifier, currencyCode, rate) =>
          Json.obj(
            "currencyId" -> Json.fromInt(currencyIdentifier),
            "currency"   -> Json.fromString(currencyCode),
            "sourceDate" -> Json.fromString(selectedDate.toString),
            "rate"       -> Json.fromBigDecimal(rate)
          )
        }),
        "stockQuotes" -> Json.fromValues(stockHistories.zip(selectedStockQuotes).map {
          case ((stockIdentifier, stockName, stooqSymbol, storedCurrency, quoteHistory, sourceUrl, observedAt, sourceJson), (_, value)) =>
            Json.obj(
              "stockId"          -> Json.fromInt(stockIdentifier),
              "name"             -> Json.fromString(stockName),
              "stooqSymbol"      -> Json.fromString(stooqSymbol),
              "yahooSymbol"      -> Json.fromString(quoteHistory.symbol),
              "currency"         -> Json.fromString(storedCurrency),
              "sourceCurrency"   -> Json.fromString(quoteHistory.currency),
              "sourceDate"       -> Json.fromString(selectedDate.toString),
              "observedAt"       -> Json.fromString(observedAt.toString),
              "sourceUrl"        -> Json.fromString(sourceUrl),
              "value"            -> Json.fromBigDecimal(value),
              "convertedFromUsd" -> Json.fromBoolean(quoteHistory.currency == "USD" && storedCurrency == "PLN"),
              "yahooResponse"    -> sourceJson
            )
        })
      )
      _ <- ZIO.attemptBlocking(Files.writeString(temporaryReportPath, report.spaces2, StandardCharsets.UTF_8))
      _ <- ZIO.attemptBlocking(Files.move(temporaryReportPath, reportPath, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING))
      _ <- ZIO.logInfo(s"Validated ${selectedStockQuotes.size} quotes and ${selectedCurrencyRates.size} FX rates for $selectedDate; report=$reportPath")
      _ <- ZIO.unless(dryRun) {
        (for {
          _ <- sql"LOCK TABLE mf_stock_quote, mf_currency_rate IN SHARE ROW EXCLUSIVE MODE".update.run
          _ <- selectedStockQuotes.traverse_ { case (stockIdentifier, value) =>
            for {
              updatedRows <- sql"UPDATE mf_stock_quote SET value = $value WHERE stock_id = $stockIdentifier AND date = $timestamp::timestamp".update.run
              _ <-
                if (updatedRows == 0) sql"INSERT INTO mf_stock_quote (stock_id, value, date) VALUES ($stockIdentifier, $value, $timestamp::timestamp)".update.run.void
                else if (updatedRows == 1) ().pure[ConnectionIO]
                else connection.raiseError[Unit](new IllegalStateException(s"Duplicate existing stock quotes for instrument $stockIdentifier on $selectedDate"))
              storedValue <- sql"SELECT value FROM mf_stock_quote WHERE stock_id = $stockIdentifier AND date = $timestamp::timestamp".query[BigDecimal].unique
              _           <- if (storedValue == value) ().pure[ConnectionIO] else connection.raiseError[Unit](new IllegalStateException(s"Stock quote read-back failed for $stockIdentifier"))
            } yield ()
          }
          _ <- selectedCurrencyRates.traverse_ { case (currencyIdentifier, currencyCode, rate) =>
            for {
              _ <- sql"""
                INSERT INTO mf_currency_rate (currency_id, rate, date) VALUES ($currencyIdentifier, $rate, $timestamp::timestamp)
                ON CONFLICT (currency_id, date) DO UPDATE SET rate = EXCLUDED.rate
              """.update.run
              storedRate <- sql"SELECT rate FROM mf_currency_rate WHERE currency_id = $currencyIdentifier AND date = $timestamp::timestamp".query[BigDecimal].unique
              _          <- if (storedRate == rate) ().pure[ConnectionIO] else connection.raiseError[Unit](new IllegalStateException(s"Currency rate read-back failed for $currencyCode"))
            } yield ()
          }
        } yield ()).transact(databaseAccess.transactor)
      }
      completedAt <- Clock.instant
      completedReport = report.deepMerge(Json.obj("status" -> Json.fromString(if (dryRun) "dry-run" else "committed"), "completedAt" -> Json.fromString(completedAt.toString)))
      _ <- ZIO.attemptBlocking(Files.writeString(temporaryReportPath, completedReport.spaces2, StandardCharsets.UTF_8))
      _ <- ZIO.attemptBlocking(Files.move(temporaryReportPath, reportPath, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING))
      _ <- ZIO.logInfo(
        s"Yahoo + NBP ${if (dryRun) "dry-run" else "import"} completed: ${selectedStockQuotes.size} quotes, ${selectedCurrencyRates.size} FX rates, stored date=$selectedDate, requested date=$requestedDate, report=$reportPath"
      )
    } yield ()

  def syntax: Command[ImportYahooNbpQuotes] =
    Command(
      "import-yahoo-nbp-quotes",
      Options.text("date").optional ++ Options.boolean("dry-run") ++ Options.text("report-directory").withDefault("/backend/market-data-imports/yahoo-nbp"),
      Args.none
    ).map { case (date, dryRun, reportDirectory) => ImportYahooNbpQuotes(date, dryRun, reportDirectory) }
      .withHelp(p("Import held instruments from Yahoo and NBP table A at a common completed date; --date is an upper bound, --dry-run validates without database writes"))

  def live: TaskLayer[SttpClient] = HttpClientZioBackend.layer()

  def dispatch: PartialFunction[CliAction, RIO[Environment, Unit]] =
    case ImportYahooNbpQuotes(date, dryRun, reportDirectory) =>
      ImportYahooNbpQuotesCommand(date, dryRun, reportDirectory)
        .provide(ImportYahooNbpQuotesCommand.live, DatabaseAccess.layer)
        .orDie
}
