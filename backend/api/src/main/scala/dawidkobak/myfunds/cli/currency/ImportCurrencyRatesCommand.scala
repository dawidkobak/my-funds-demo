package dawidkobak.myfunds.cli.currency

import java.time.{LocalDate, ZoneId}

import cats.syntax.all.*
import dawidkobak.myfunds.cli.CliApplication.Environment
import dawidkobak.myfunds.cli.model.*
import dawidkobak.myfunds.cli.stock.{MarketDataHttpException, YahooNbpImportHelper}
import dawidkobak.myfunds.domain.DatabaseAccess
import dawidkobak.myfunds.implicits.SttpClientImplicits.*
import doobie.ConnectionIO
import doobie.free.connection
import doobie.implicits.*
import sttp.client3.*
import sttp.client3.httpclient.zio.{HttpClientZioBackend, SttpClient}
import zio.*
import zio.cli.{Args, Command, Options}
import zio.cli.HelpDoc.p
import zio.interop.catz.*

private final case class ImportCurrencyRates(date: Option[String], dryRun: Boolean) extends CliAction

object ImportCurrencyRatesCommand extends RunnableCommand[ImportCurrencyRates] {
  def apply(dateCliArgument: Option[String], dryRun: Boolean = false): RIO[DatabaseAccess & SttpClient, Unit] =
    for {
      databaseAccess <- ZIO.service[DatabaseAccess]
      httpClient <- ZIO.service[SttpClient]
      today <- Clock.instant.map(_.atZone(ZoneId.of("Europe/Warsaw")).toLocalDate)
      requestedDate <- ZIO.attempt(dateCliArgument.map(LocalDate.parse).getOrElse(today))
      _ <- ZIO.fail(new IllegalArgumentException(s"Cannot import future FX data for $requestedDate")).when(requestedDate.isAfter(today))
      currencies <- sql"SELECT mf_currency.id, mf_currency.code FROM mf_currency WHERE mf_currency.code <> 'PLN' ORDER BY mf_currency.id"
        .query[(Int, String)].to[List].transact(databaseAccess.transactor)
      _ <- ZIO.fail(new IllegalArgumentException("No foreign currencies configured for NBP import")).when(currencies.isEmpty)
      historyStartDate = requestedDate.minusDays(14)
      response <- (for {
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
      ratesByCurrency <- ZIO.attempt(YahooNbpImportHelper.nbpRates(response.body, currencies.map(_._2).toSet, requestedDate))
      selectedDate <- ZIO.attempt(YahooNbpImportHelper.commonDate(requestedDate, ratesByCurrency.values.map(_.keySet).toList))
      selectedRates = currencies.map { case (currencyIdentifier, currencyCode) =>
        (currencyIdentifier, currencyCode, ratesByCurrency(currencyCode)(selectedDate).setScale(8, BigDecimal.RoundingMode.HALF_UP))
      }
      timestamp = s"$selectedDate 00:00:00"
      _ <- ZIO.logInfo(s"Validated NBP table A rates: requested=$requestedDate, stored=$selectedDate, dry-run=$dryRun")
      _ <- ZIO.foreachDiscard(selectedRates) { case (_, currencyCode, rate) =>
        ZIO.logInfo(s"Currency rate: $currencyCode/PLN = $rate")
      }
      _ <- ZIO.unless(dryRun) {
        (for {
          _ <- sql"LOCK TABLE mf_currency_rate IN SHARE ROW EXCLUSIVE MODE".update.run
          _ <- selectedRates.traverse_ { case (currencyIdentifier, currencyCode, rate) =>
            for {
              _ <- sql"""
                INSERT INTO mf_currency_rate (currency_id, rate, date)
                VALUES ($currencyIdentifier, $rate, $timestamp::timestamp)
                ON CONFLICT (currency_id, date) DO UPDATE SET rate = EXCLUDED.rate
              """.update.run
              storedRate <- sql"SELECT rate FROM mf_currency_rate WHERE currency_id = $currencyIdentifier AND date = $timestamp::timestamp"
                .query[BigDecimal].unique
              _ <- if (storedRate == rate) ().pure[ConnectionIO]
                   else connection.raiseError[Unit](new IllegalStateException(s"Currency rate read-back failed for $currencyCode"))
            } yield ()
          }
        } yield ()).transact(databaseAccess.transactor)
      }
      _ <- ZIO.logInfo(s"NBP currency ${if (dryRun) "dry-run" else "import"} completed: ${selectedRates.size} rates for $selectedDate")
    } yield ()

  def syntax: Command[ImportCurrencyRates] =
    Command("import-currency-rates", Options.text("date").optional ++ Options.boolean("dry-run"), Args.none)
      .map { case (date, dryRun) => ImportCurrencyRates(date, dryRun) }
      .withHelp(p("Import NBP table A mid rates in PLN at a common date; --date is an upper bound, --dry-run validates without database writes"))

  def live: TaskLayer[SttpClient] = HttpClientZioBackend.layer()

  def dispatch: PartialFunction[CliAction, RIO[Environment, Unit]] =
    case ImportCurrencyRates(date, dryRun) =>
      ImportCurrencyRatesCommand(date, dryRun)
        .provide(ImportCurrencyRatesCommand.live, DatabaseAccess.layer)
}
