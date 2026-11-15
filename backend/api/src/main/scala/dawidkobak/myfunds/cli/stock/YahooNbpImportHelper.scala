package dawidkobak.myfunds.cli.stock

import java.time.{Instant, LocalDate, ZoneId}
import java.util.Locale

import io.circe.Json
import io.circe.parser.parse

final case class YahooQuoteHistory(symbol: String, currency: String, valuesByDate: Map[LocalDate, BigDecimal])

final case class MarketDataHttpException(source: String, statusCode: Int) extends RuntimeException(s"$source returned HTTP $statusCode instead of market data")

object YahooNbpImportHelper {
  def yahooSymbol(stooqSymbol: String): String =
    stooqSymbol.trim.toLowerCase(Locale.ROOT) match {
      case "btcpln"                                                                           => "BTC-USD"
      case "bnb.v"                                                                            => "BNB-USD"
      case symbol if symbol.endsWith(".us")                                                   => symbol.stripSuffix(".us").toUpperCase(Locale.ROOT)
      case symbol if symbol.endsWith(".uk")                                                   => s"${symbol.stripSuffix(".uk").toUpperCase(Locale.ROOT)}.L"
      case symbol if symbol.endsWith(".nl")                                                   => s"${symbol.stripSuffix(".nl").toUpperCase(Locale.ROOT)}.AS"
      case symbol if symbol.endsWith(".pl")                                                   => s"${symbol.stripSuffix(".pl").toUpperCase(Locale.ROOT)}.WA"
      case symbol if symbol.nonEmpty && symbol.forall(character => character.isLetterOrDigit) => s"${symbol.toUpperCase(Locale.ROOT)}.WA"
      case _                                                                                  => throw new IllegalArgumentException(s"Unsupported Stooq symbol for Yahoo mapping: $stooqSymbol")
    }

  def yahooQuotes(responseBody: String, expectedSymbol: String, storedCurrency: String, requestedDate: LocalDate, observedAt: Instant): YahooQuoteHistory = {
    val responseJson = parse(responseBody).fold(throw _, identity)
    val chartCursor  = responseJson.hcursor.downField("chart")
    require(chartCursor.downField("error").focus.forall(_.isNull), s"Yahoo returned an error for $expectedSymbol")
    val results = chartCursor.get[Vector[Json]]("result").fold(throw _, identity)
    require(results.size == 1, s"Expected one Yahoo series for $expectedSymbol")
    val resultCursor = results.head.hcursor
    val metadata     = resultCursor.downField("meta")
    val symbol       = metadata.get[String]("symbol").fold(throw _, identity)
    val currency     = metadata.get[String]("currency").fold(throw _, identity)
    require(symbol == expectedSymbol, s"Yahoo symbol mismatch: expected $expectedSymbol, received $symbol")
    val normalizedCurrency = if (currency == "GBp" || currency == "GBX") "GBP" else currency
    require(
      normalizedCurrency == storedCurrency || (expectedSymbol == "BTC-USD" && storedCurrency == "PLN" && currency == "USD"),
      s"Yahoo currency mismatch for $expectedSymbol: expected $storedCurrency, received $currency"
    )
    val exchangeTimezone  = ZoneId.of(metadata.get[String]("exchangeTimezoneName").fold(throw _, identity))
    val observedDate      = observedAt.atZone(exchangeTimezone).toLocalDate
    val instrumentType    = metadata.get[String]("instrumentType").fold(throw _, identity)
    val currentSessionEnd = metadata.downField("currentTradingPeriod").downField("regular").get[Long]("end").toOption.map(Instant.ofEpochSecond)
    val timestamps        = resultCursor.get[Vector[Long]]("timestamp").fold(throw _, identity)
    val quoteSeries       = resultCursor.downField("indicators").get[Vector[Json]]("quote").fold(throw _, identity)
    require(quoteSeries.size == 1, s"Expected one Yahoo close series for $expectedSymbol")
    val closingValues = quoteSeries.head.hcursor.get[Vector[Option[BigDecimal]]]("close").fold(throw _, identity)
    require(timestamps.size == closingValues.size, s"Yahoo timestamp/close length mismatch for $expectedSymbol")
    val values = timestamps.zip(closingValues).flatMap { case (timestamp, closingValue) =>
      val sourceDate = Instant.ofEpochSecond(timestamp).atZone(exchangeTimezone).toLocalDate
      val completed = sourceDate.isBefore(observedDate) || (
        sourceDate == observedDate && instrumentType != "CRYPTOCURRENCY" &&
          currentSessionEnd.exists(sessionEnd => sessionEnd.atZone(exchangeTimezone).toLocalDate == sourceDate && !observedAt.isBefore(sessionEnd.plusSeconds(1800)))
      )
      if (completed && !sourceDate.isAfter(requestedDate)) {
        closingValue.map { value =>
          require(value > 0, s"Yahoo returned a non-positive close for $expectedSymbol on $sourceDate")
          sourceDate -> (if (currency == "GBp" || currency == "GBX") value / BigDecimal(100) else value)
        }
      } else None
    }
    require(values.nonEmpty, s"Yahoo returned no completed quotes on or before $requestedDate for $expectedSymbol")
    require(values.map(_._1).distinct.size == values.size, s"Yahoo returned duplicate quote dates for $expectedSymbol")
    YahooQuoteHistory(symbol, normalizedCurrency, values.toMap)
  }

  def nbpRates(responseBody: String, requiredCurrencies: Set[String], requestedDate: LocalDate): Map[String, Map[LocalDate, BigDecimal]] = {
    val tables = parse(responseBody).fold(throw _, identity).as[Vector[Json]].fold(throw _, identity)
    require(tables.nonEmpty, "NBP returned no exchange rate tables")
    val datedRates = tables.map { table =>
      val tableCursor = table.hcursor
      require(tableCursor.get[String]("table").fold(throw _, identity) == "A", "Expected NBP table A with mid rates")
      val sourceDate = LocalDate.parse(tableCursor.get[String]("effectiveDate").fold(throw _, identity))
      require(!sourceDate.isAfter(requestedDate), s"NBP returned a future table: $sourceDate")
      val rates = tableCursor.get[Vector[Json]]("rates").fold(throw _, identity).map { rate =>
        val code  = rate.hcursor.get[String]("code").fold(throw _, identity)
        val value = rate.hcursor.get[BigDecimal]("mid").fold(throw _, identity)
        require(value > 0, s"NBP returned a non-positive mid rate for $code on $sourceDate")
        code -> value
      }
      require(rates.map(_._1).distinct.size == rates.size, s"NBP returned duplicate currency codes on $sourceDate")
      sourceDate -> rates.toMap
    }
    require(datedRates.map(_._1).distinct.size == datedRates.size, "NBP returned duplicate table dates")
    requiredCurrencies.map { currency =>
      val values = datedRates.flatMap { case (sourceDate, rates) => rates.get(currency).map(sourceDate -> _) }.toMap
      require(values.nonEmpty, s"NBP table A does not cover required currency $currency")
      currency -> values
    }.toMap
  }

  def commonDate(requestedDate: LocalDate, availableDates: List[Set[LocalDate]]): LocalDate = {
    require(availableDates.nonEmpty, "Cannot import an empty market data set")
    (0 to 7)
      .map(daysBefore => requestedDate.minusDays(daysBefore.toLong))
      .find(candidateDate => availableDates.forall(_.contains(candidateDate)))
      .getOrElse(throw new IllegalArgumentException(s"No common completed quote/FX date within 7 calendar days on or before $requestedDate"))
  }
}
