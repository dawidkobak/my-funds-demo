package dawidkobak.myfunds.expenses

import java.io.{ByteArrayInputStream, InputStreamReader, StringReader}
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.time.format.DateTimeFormatter
import java.time.{LocalDate, YearMonth}

import io.circe.Json
import io.circe.parser.parse
import org.apache.commons.csv.{CSVFormat, CSVParser}
import org.apache.poi.ss.usermodel.{Cell, CellType, DataFormatter, DateUtil, FormulaEvaluator}
import org.apache.poi.xssf.usermodel.XSSFWorkbook

import scala.jdk.CollectionConverters.*
import scala.util.Try

final case class ExpenseImportRow(
  rowNumber: Int,
  folderPath: Option[String],
  paymentSource: Option[String],
  category: Option[String],
  tags: List[String],
  name: String,
  note: Option[String],
  amount: BigDecimal,
  currency: String,
  expenseMonth: LocalDate,
  expenseDate: Option[LocalDate],
  entryType: String,
  fingerprint: String,
  warnings: List[String],
  paymentSourceType: Option[String] = None,
  categoryColor: Option[String] = None,
  categoryIcon: Option[String] = None,
  countingStatus: String = "included"
)

final case class ExpenseImportPreview(
  format: String,
  fileName: String,
  checksum: String,
  rows: List[ExpenseImportRow],
  warnings: List[String]
)

object ExpenseImportParser {
  private val historicalFileName  = "(?i)^wydatki_([0-9]{2})_([0-9]{2})\\.xlsx$".r
  private val polishDateFormatter = DateTimeFormatter.ofPattern("dd.MM.yyyy")
  private val monthNames = List(
    "Styczeń",
    "Luty",
    "Marzec",
    "Kwiecień",
    "Maj",
    "Czerwiec",
    "Lipiec",
    "Sierpień",
    "Wrzesień",
    "Październik",
    "Listopad",
    "Grudzień"
  )

  def parseFile(fileName: String, content: Array[Byte]): ExpenseImportPreview =
    fileName.toLowerCase match {
      case fileNameLowerCase if fileNameLowerCase.endsWith(".xlsx") =>
        fileName match {
          case historicalFileName(_, _) => parseHistoricalWorkbook(fileName, content)
          case _                        => parseBankWorkbook(fileName, content)
        }
      case fileNameLowerCase if fileNameLowerCase.endsWith(".json") => parseNativeJson(fileName, content)
      case fileNameLowerCase if fileNameLowerCase.endsWith(".csv") =>
        if (isPekaoCsv(content)) parsePekaoCsv(fileName, content) else parseNativeCsv(fileName, content)
      case _ => throw new IllegalArgumentException("Obsługiwane formaty etapu 1 to XLSX, JSON i CSV")
    }

  private def parseBankWorkbook(fileName: String, content: Array[Byte]): ExpenseImportPreview = {
    val workbook         = new XSSFWorkbook(new ByteArrayInputStream(content))
    val dataFormatter    = new DataFormatter(java.util.Locale.forLanguageTag("pl-PL"))
    val formulaEvaluator = workbook.getCreationHelper.createFormulaEvaluator()
    val headers =
      try {
        val worksheet = workbook.getSheetAt(0)
        Option(worksheet.getRow(worksheet.getFirstRowNum)).toList
          .flatMap(_.cellIterator().asScala)
          .map(dataFormatter.formatCellValue(_, formulaEvaluator).trim)
          .filter(_.nonEmpty)
          .toSet
      } finally workbook.close()

    if (Set("Data transakcji", "Data zaksięgowania", "Kwota", "Waluta", "Typ transakcji", "Status").subsetOf(headers)) {
      parseBnpWorkbook(fileName, content)
    } else if (Set("Rodzaj", "Data rozpoczęcia", "Opis", "Kwota", "Waluta", "State").subsetOf(headers)) {
      parseRevolutWorkbook(fileName, content)
    } else {
      throw new IllegalArgumentException("Nie rozpoznano formatu XLSX. Obsługiwane są historyczne wydatki, zestawienie BGŻ/BNP i wyciąg Revolut")
    }
  }

  private def isPekaoCsv(content: Array[Byte]): Boolean = {
    val firstLine = String(content, StandardCharsets.UTF_8).stripPrefix("\uFEFF").linesIterator.nextOption().getOrElse("")
    List("Data księgowania", "Kwota operacji", "Waluta", "Numer referencyjny", "Typ operacji").forall(firstLine.contains)
  }

  private def parseHistoricalWorkbook(fileName: String, content: Array[Byte]): ExpenseImportPreview = {
    val expenseMonth = fileName match {
      case historicalFileName(shortYear, month) =>
        val parsedMonth = month.toInt
        if (parsedMonth < 1 || parsedMonth > 12) {
          throw new IllegalArgumentException("Miesiąc w nazwie pliku musi mieścić się w zakresie 01-12")
        }
        YearMonth.of(2000 + shortYear.toInt, parsedMonth).atDay(1)
      case _ => throw new IllegalArgumentException("Historyczny XLSX musi mieć nazwę wydatki_YY_MM.xlsx")
    }
    val workbook          = new XSSFWorkbook(new ByteArrayInputStream(content))
    val dataFormatter     = new DataFormatter(java.util.Locale.forLanguageTag("pl-PL"))
    val formulaEvaluator  = workbook.getCreationHelper.createFormulaEvaluator()
    val occurrences       = scala.collection.mutable.Map.empty[String, Int]
    val parsedRows        = scala.collection.mutable.ListBuffer.empty[ExpenseImportRow]
    var paymentSourceName = Option.empty[String]

    try {
      val worksheet = workbook.getSheetAt(0)
      val amountFromCell: Cell => Option[BigDecimal] = spreadsheetCell =>
        if (spreadsheetCell.getCellType == CellType.NUMERIC) {
          Some(BigDecimal(spreadsheetCell.getNumericCellValue.toString))
        } else if (spreadsheetCell.getCellType == CellType.FORMULA && formulaEvaluator.evaluate(spreadsheetCell).getCellType == CellType.NUMERIC) {
          Some(BigDecimal(formulaEvaluator.evaluate(spreadsheetCell).getNumberValue.toString))
        } else {
          Try(
            dataFormatter
              .formatCellValue(spreadsheetCell, formulaEvaluator)
              .replace(" ", "")
              .replace(" ", "")
              .replace(",", ".")
              .trim
          ).toOption.filter(_.nonEmpty).flatMap(value => Try(BigDecimal(value)).toOption)
        }
      val selectedColumns = List((0, 1), (1, 2), (2, 3))
        .map { case (nameColumnIndex, amountColumnIndex) =>
          val numericValues = (worksheet.getFirstRowNum to worksheet.getLastRowNum).count { rowIndex =>
            Option(worksheet.getRow(rowIndex))
              .flatMap(spreadsheetRow => Option(spreadsheetRow.getCell(amountColumnIndex)))
              .flatMap(amountFromCell)
              .nonEmpty
          }
          (nameColumnIndex, amountColumnIndex, numericValues)
        }
        .maxBy(_._3)
      val nameColumnIndex   = selectedColumns._1
      val amountColumnIndex = selectedColumns._2
      val sourceColumnIndex = if (nameColumnIndex > 0) nameColumnIndex - 1 else amountColumnIndex + 1
      (worksheet.getFirstRowNum to worksheet.getLastRowNum).foreach { rowIndex =>
        val spreadsheetRow = worksheet.getRow(rowIndex)
        if (spreadsheetRow != null) {
          val sourceValue = Option(spreadsheetRow.getCell(sourceColumnIndex))
            .map(dataFormatter.formatCellValue(_, formulaEvaluator).trim)
            .filter(_.nonEmpty)
          val nameValue = Option(spreadsheetRow.getCell(nameColumnIndex))
            .map(dataFormatter.formatCellValue(_, formulaEvaluator).trim)
            .filter(_.nonEmpty)
          val amountValue = Option(spreadsheetRow.getCell(amountColumnIndex)).flatMap(amountFromCell)

          if (sourceValue.nonEmpty && nameValue.isEmpty && amountValue.isEmpty) {
            paymentSourceName = sourceValue.map {
              case source if source.equalsIgnoreCase("BGŻ") || source.equalsIgnoreCase("BGZ") => "BNP Paribas"
              case source if source.equalsIgnoreCase("Revlut")                                => "Revolut"
              case source if source.equalsIgnoreCase("PKO")                                   => "Pekao"
              case source if source.equalsIgnoreCase("Revolut")                               => "Revolut"
              case source if source.equalsIgnoreCase("Pekao")                                 => "Pekao"
              case source                                                                     => source
            }
          } else if (amountValue.nonEmpty) {
            val normalizedSource = paymentSourceName.map(_.trim)
            val importedName     = nameValue.getOrElse("Nieopisany wydatek")
            val additionalNote = sourceValue
              .filter(_ => nameValue.nonEmpty)
              .map(value => s"; informacja z arkusza: $value")
              .getOrElse("")
            val baseFingerprint = List(
              "xlsx",
              expenseMonth.toString,
              normalizedSource.getOrElse("missing"),
              importedName.trim,
              amountValue.get.bigDecimal.toPlainString,
              "PLN"
            ).mkString("|").toLowerCase
            val occurrenceNumber = occurrences.getOrElse(baseFingerprint, 0) + 1
            occurrences.update(baseFingerprint, occurrenceNumber)
            parsedRows += ExpenseImportRow(
              rowNumber = rowIndex + 1,
              folderPath = Some(s"${expenseMonth.getYear}/${monthNames(expenseMonth.getMonthValue - 1)}"),
              paymentSource = normalizedSource,
              category = None,
              tags = List.empty,
              name = importedName,
              note = Some(s"Import z $fileName, wiersz ${rowIndex + 1}$additionalNote"),
              amount = amountValue.get,
              currency = "PLN",
              expenseMonth = expenseMonth,
              expenseDate = None,
              entryType = if (amountValue.get < 0) "refund" else "expense",
              fingerprint = sha256(s"$baseFingerprint|$occurrenceNumber"),
              warnings = if (normalizedSource.isEmpty) List("Brak źródła płatności — wybierz je przed zatwierdzeniem") else List.empty
            )
          }
        }
      }
    } finally workbook.close()

    val historicalTransferRows = parsedRows.toList.flatMap { parsedRow =>
      val normalizedTransferName = normalizedText(parsedRow.name).toLowerCase
        .replace('ż', 'z')
        .replaceAll("[–—-]+", " ")
        .replaceAll("\\s+", " ")
        .replaceFirst("^przelew do ", "przelew ")
        .trim
      Option
        .when(Set("przelew revolut", "przelew bgz", "przelew pekao", "przelew pko").contains(normalizedTransferName))(
          ((parsedRow.expenseMonth, parsedRow.currency, parsedRow.amount.abs), normalizedTransferName.stripPrefix("przelew "))
        )
    }
    val matchedHistoricalTransferKeys = historicalTransferRows
      .groupBy(_._1)
      .collect { case (transferKey, matchingTransferRows) if matchingTransferRows.map(_._2).distinct.size >= 2 => transferKey }
      .toSet
    val classifiedRows = parsedRows.toList.map { parsedRow =>
      val normalizedTransferName = normalizedText(parsedRow.name).toLowerCase
        .replace('ż', 'z')
        .replaceAll("[–—-]+", " ")
        .replaceAll("\\s+", " ")
        .replaceFirst("^przelew do ", "przelew ")
        .trim
      if (
        Set("przelew revolut", "przelew bgz", "przelew pekao", "przelew pko").contains(normalizedTransferName) &&
        matchedHistoricalTransferKeys.contains((parsedRow.expenseMonth, parsedRow.currency, parsedRow.amount.abs))
      ) {
        parsedRow.copy(countingStatus = "excluded_internal_transfer")
      } else parsedRow
    }

    ExpenseImportPreview(
      format = "historical-xlsx",
      fileName = fileName,
      checksum = sha256(content),
      rows = classifiedRows,
      warnings = if (parsedRows.exists(_.paymentSource.isEmpty)) List("Część wpisów nie ma źródła płatności") else List.empty
    )
  }

  private def parseBnpWorkbook(fileName: String, content: Array[Byte]): ExpenseImportPreview = {
    val workbook            = new XSSFWorkbook(new ByteArrayInputStream(content))
    val dataFormatter       = new DataFormatter(java.util.Locale.forLanguageTag("pl-PL"))
    val formulaEvaluator    = workbook.getCreationHelper.createFormulaEvaluator()
    val parsedRows          = scala.collection.mutable.ListBuffer.empty[ExpenseImportRow]
    var skippedIncomes      = 0
    var skippedOwnTransfers = 0
    var skippedUnfinished   = 0
    var skippedInvalid      = 0

    try {
      val worksheet = workbook.getSheetAt(0)
      val headerRow = worksheet.getRow(worksheet.getFirstRowNum)
      val headerIndexes = (0 until headerRow.getLastCellNum.toInt).flatMap { columnIndex =>
        Option(headerRow.getCell(columnIndex))
          .map(cell => cellText(cell, dataFormatter, formulaEvaluator))
          .filter(_.nonEmpty)
          .map(_ -> columnIndex)
      }.toMap

      (worksheet.getFirstRowNum + 1 to worksheet.getLastRowNum).foreach { rowIndex =>
        val spreadsheetRow = worksheet.getRow(rowIndex)
        if (spreadsheetRow != null) {
          val transactionDate = Option(spreadsheetRow.getCell(headerIndexes("Data transakcji"))).flatMap(cellDate(_, dataFormatter, formulaEvaluator))
          val bookedDate      = Option(spreadsheetRow.getCell(headerIndexes("Data zaksięgowania"))).flatMap(cellDate(_, dataFormatter, formulaEvaluator))
          val amount          = Option(spreadsheetRow.getCell(headerIndexes("Kwota"))).flatMap(cellAmount(_, dataFormatter, formulaEvaluator))
          val currency        = Option(spreadsheetRow.getCell(headerIndexes("Waluta"))).map(cellText(_, dataFormatter, formulaEvaluator)).filter(_.nonEmpty)
          val recipient       = Option(spreadsheetRow.getCell(headerIndexes("Odbiorca"))).map(cellText(_, dataFormatter, formulaEvaluator)).getOrElse("")
          val description     = Option(spreadsheetRow.getCell(headerIndexes("Opis"))).map(cellText(_, dataFormatter, formulaEvaluator)).getOrElse("")
          val transactionType = Option(spreadsheetRow.getCell(headerIndexes("Typ transakcji"))).map(cellText(_, dataFormatter, formulaEvaluator)).getOrElse("")
          val status          = Option(spreadsheetRow.getCell(headerIndexes("Status"))).map(cellText(_, dataFormatter, formulaEvaluator)).getOrElse("")
          val balance = headerIndexes.get("Saldo po transakcji").flatMap(columnIndex => Option(spreadsheetRow.getCell(columnIndex))).map(cellText(_, dataFormatter, formulaEvaluator)).getOrElse("")
          val recipientName         = meaningfulName(recipient).getOrElse(normalizedText(description)).take(255)
          val normalizedRecipient   = normalizedText(recipient).toLowerCase
          val normalizedDescription = normalizedText(description).toLowerCase
          val ownTransfer = transactionType.equalsIgnoreCase("Przelew wychodzący") &&
            (normalizedRecipient.contains("transfer własny") || normalizedRecipient.contains("przelew własny")) &&
            normalizedDescription == "przelew środków"

          (transactionDate, amount, currency) match {
            case (Some(selectedDate), Some(sourceAmount), Some(selectedCurrency)) if status.equalsIgnoreCase("Zrealizowana") && sourceAmount < 0 && !ownTransfer =>
              val fingerprint = sha256(
                List(
                  "bnp-xlsx",
                  selectedDate,
                  bookedDate.getOrElse(selectedDate),
                  sourceAmount.bigDecimal.toPlainString,
                  selectedCurrency,
                  normalizedText(recipient),
                  normalizedText(description),
                  transactionType,
                  balance
                ).mkString("|").toLowerCase
              )
              parsedRows += bankImportRow(
                rowNumber = rowIndex + 1,
                paymentSource = "BNP Paribas",
                name = if (recipientName.nonEmpty) recipientName else "Nieopisany wydatek BGŻ/BNP",
                note = bankNote("BGŻ/BNP", fileName, rowIndex + 1, description, transactionType),
                amount = -sourceAmount,
                currency = selectedCurrency,
                expenseDate = selectedDate,
                entryType = "expense",
                fingerprint = fingerprint
              )
            case (Some(selectedDate), Some(sourceAmount), Some(selectedCurrency))
                if status.equalsIgnoreCase("Zrealizowana") && sourceAmount > 0 && transactionType.equalsIgnoreCase("Transakcja BLIK") =>
              val fingerprint = sha256(
                List(
                  "bnp-xlsx",
                  selectedDate,
                  bookedDate.getOrElse(selectedDate),
                  sourceAmount.bigDecimal.toPlainString,
                  selectedCurrency,
                  normalizedText(recipient),
                  normalizedText(description),
                  transactionType,
                  balance
                ).mkString("|").toLowerCase
              )
              parsedRows += bankImportRow(
                rowNumber = rowIndex + 1,
                paymentSource = "BNP Paribas",
                name = if (recipientName.nonEmpty) recipientName else "Zwrot BGŻ/BNP",
                note = bankNote("BGŻ/BNP", fileName, rowIndex + 1, description, transactionType),
                amount = -sourceAmount,
                currency = selectedCurrency,
                expenseDate = selectedDate,
                entryType = "refund",
                fingerprint = fingerprint
              )
            case (_, _, _) if !status.equalsIgnoreCase("Zrealizowana")         => skippedUnfinished += 1
            case (_, Some(sourceAmount), _) if ownTransfer && sourceAmount < 0 => skippedOwnTransfers += 1
            case (_, Some(sourceAmount), _) if sourceAmount >= 0               => skippedIncomes += 1
            case _                                                             => skippedInvalid += 1
          }
        }
      }
    } finally workbook.close()

    ExpenseImportPreview(
      format = "bnp-xlsx",
      fileName = fileName,
      checksum = sha256(content),
      rows = parsedRows.toList,
      warnings = skippedWarnings(skippedIncomes, skippedOwnTransfers, 0, skippedUnfinished, skippedInvalid, 0)
    )
  }

  private def parseRevolutWorkbook(fileName: String, content: Array[Byte]): ExpenseImportPreview = {
    val workbook          = new XSSFWorkbook(new ByteArrayInputStream(content))
    val dataFormatter     = new DataFormatter(java.util.Locale.forLanguageTag("pl-PL"))
    val formulaEvaluator  = workbook.getCreationHelper.createFormulaEvaluator()
    val parsedRows        = scala.collection.mutable.ListBuffer.empty[ExpenseImportRow]
    var skippedIncomes    = 0
    var skippedExchanges  = 0
    var skippedUnfinished = 0
    var skippedInvalid    = 0
    var nonZeroFees       = 0

    try {
      val worksheet = workbook.getSheetAt(0)
      val headerRow = worksheet.getRow(worksheet.getFirstRowNum)
      val headerIndexes = (0 until headerRow.getLastCellNum.toInt).flatMap { columnIndex =>
        Option(headerRow.getCell(columnIndex))
          .map(cell => cellText(cell, dataFormatter, formulaEvaluator))
          .filter(_.nonEmpty)
          .map(_ -> columnIndex)
      }.toMap

      (worksheet.getFirstRowNum + 1 to worksheet.getLastRowNum).foreach { rowIndex =>
        val spreadsheetRow = worksheet.getRow(rowIndex)
        if (spreadsheetRow != null) {
          val transactionDate = Option(spreadsheetRow.getCell(headerIndexes("Data rozpoczęcia"))).flatMap(cellDate(_, dataFormatter, formulaEvaluator))
          val completionDate  = Option(spreadsheetRow.getCell(headerIndexes("Data zrealizowania"))).flatMap(cellDate(_, dataFormatter, formulaEvaluator))
          val amount          = Option(spreadsheetRow.getCell(headerIndexes("Kwota"))).flatMap(cellAmount(_, dataFormatter, formulaEvaluator))
          val fee = headerIndexes.get("Opłata").flatMap(columnIndex => Option(spreadsheetRow.getCell(columnIndex))).flatMap(cellAmount(_, dataFormatter, formulaEvaluator)).getOrElse(BigDecimal(0))
          val currency        = Option(spreadsheetRow.getCell(headerIndexes("Waluta"))).map(cellText(_, dataFormatter, formulaEvaluator)).filter(_.nonEmpty)
          val description     = Option(spreadsheetRow.getCell(headerIndexes("Opis"))).map(cellText(_, dataFormatter, formulaEvaluator)).getOrElse("")
          val transactionType = Option(spreadsheetRow.getCell(headerIndexes("Rodzaj"))).map(cellText(_, dataFormatter, formulaEvaluator)).getOrElse("")
          val status          = Option(spreadsheetRow.getCell(headerIndexes("State"))).map(cellText(_, dataFormatter, formulaEvaluator)).getOrElse("")
          val balance         = headerIndexes.get("Saldo").flatMap(columnIndex => Option(spreadsheetRow.getCell(columnIndex))).map(cellText(_, dataFormatter, formulaEvaluator)).getOrElse("")
          if (fee != 0) {
            nonZeroFees += 1
          }

          (transactionDate, amount, currency) match {
            case (Some(selectedDate), Some(sourceAmount), Some(selectedCurrency)) if status.equalsIgnoreCase("ZAKOŃCZONO") && sourceAmount < 0 && !transactionType.equalsIgnoreCase("Wymiana") =>
              val fingerprint = sha256(
                List(
                  "revolut-xlsx",
                  selectedDate,
                  completionDate.getOrElse(selectedDate),
                  sourceAmount.bigDecimal.toPlainString,
                  fee.bigDecimal.toPlainString,
                  selectedCurrency,
                  normalizedText(description),
                  transactionType,
                  status,
                  balance
                ).mkString("|").toLowerCase
              )
              parsedRows += bankImportRow(
                rowNumber = rowIndex + 1,
                paymentSource = "Revolut",
                name = Option(normalizedText(description)).filter(_.nonEmpty).getOrElse("Nieopisany wydatek Revolut").take(255),
                note = bankNote("Revolut", fileName, rowIndex + 1, "", transactionType),
                amount = -sourceAmount,
                currency = selectedCurrency,
                expenseDate = selectedDate,
                entryType = "expense",
                fingerprint = fingerprint,
                warnings = if (fee != 0) List(s"Opłata Revolut $fee $selectedCurrency nie została dodana do kwoty") else List.empty
              )
            case (Some(selectedDate), Some(sourceAmount), Some(selectedCurrency)) if status.equalsIgnoreCase("ZAKOŃCZONO") && sourceAmount > 0 && transactionType.equalsIgnoreCase("Zwrot na kartę") =>
              val fingerprint = sha256(
                List(
                  "revolut-xlsx",
                  selectedDate,
                  completionDate.getOrElse(selectedDate),
                  sourceAmount.bigDecimal.toPlainString,
                  selectedCurrency,
                  normalizedText(description),
                  transactionType,
                  status,
                  balance
                ).mkString("|").toLowerCase
              )
              parsedRows += bankImportRow(
                rowNumber = rowIndex + 1,
                paymentSource = "Revolut",
                name = Option(normalizedText(description)).filter(_.nonEmpty).getOrElse("Zwrot Revolut").take(255),
                note = bankNote("Revolut", fileName, rowIndex + 1, "", transactionType),
                amount = -sourceAmount,
                currency = selectedCurrency,
                expenseDate = selectedDate,
                entryType = "refund",
                fingerprint = fingerprint
              )
            case (_, _, _) if !status.equalsIgnoreCase("ZAKOŃCZONO")      => skippedUnfinished += 1
            case (_, _, _) if transactionType.equalsIgnoreCase("Wymiana") => skippedExchanges += 1
            case (_, Some(sourceAmount), _) if sourceAmount >= 0          => skippedIncomes += 1
            case _                                                        => skippedInvalid += 1
          }
        }
      }
    } finally workbook.close()

    ExpenseImportPreview(
      format = "revolut-xlsx",
      fileName = fileName,
      checksum = sha256(content),
      rows = parsedRows.toList,
      warnings = skippedWarnings(skippedIncomes, 0, skippedExchanges, skippedUnfinished, skippedInvalid, nonZeroFees)
    )
  }

  private def parsePekaoCsv(fileName: String, content: Array[Byte]): ExpenseImportPreview = {
    val csvFormat = CSVFormat.DEFAULT
      .builder()
      .setDelimiter(';')
      .setHeader()
      .setSkipHeaderRecord(true)
      .setIgnoreEmptyLines(true)
      .build()
    val csvParser           = new CSVParser(new StringReader(String(content, StandardCharsets.UTF_8).stripPrefix("\uFEFF")), csvFormat)
    val parsedRows          = scala.collection.mutable.ListBuffer.empty[ExpenseImportRow]
    var skippedIncomes      = 0
    var skippedOwnTransfers = 0
    var skippedInvalid      = 0

    try {
      csvParser.iterator().asScala.zipWithIndex.foreach { case (record, index) =>
        val rowNumber       = index + 2
        val transactionDate = Try(LocalDate.parse(record.get("Data księgowania").trim, polishDateFormatter)).toOption
        val valueDate       = Try(LocalDate.parse(record.get("Data waluty").trim, polishDateFormatter)).toOption
        val amount          = Try(BigDecimal(record.get("Kwota operacji").replace(" ", "").replace(" ", "").replace(",", ".").trim)).toOption
        val currency        = Option(record.get("Waluta")).map(_.trim).filter(_.nonEmpty)
        val counterparty    = Option(record.get("Nadawca / Odbiorca")).map(normalizedText).getOrElse("")
        val title           = Option(record.get("Tytułem")).map(normalizedText).getOrElse("")
        val transactionType = Option(record.get("Typ operacji")).map(normalizedText).getOrElse("")
        val reference       = Option(record.get("Numer referencyjny")).map(_.trim.stripPrefix("'")).getOrElse("")
        val ownTransfer = transactionType.equalsIgnoreCase("PRZELEW INTERNET M/B") &&
          (counterparty.toLowerCase.contains("transfer własny") || counterparty.toLowerCase.contains("przelew własny")) &&
          title.equalsIgnoreCase("Przelew środków")

        (transactionDate, amount, currency) match {
          case (Some(selectedDate), Some(sourceAmount), Some(selectedCurrency)) if sourceAmount < 0 && !ownTransfer =>
            val fingerprint = sha256(
              List(
                "pekao-csv",
                reference,
                selectedDate,
                valueDate.getOrElse(selectedDate),
                sourceAmount.bigDecimal.toPlainString,
                selectedCurrency,
                counterparty,
                title,
                transactionType
              ).mkString("|").toLowerCase
            )
            parsedRows += bankImportRow(
              rowNumber = rowNumber,
              paymentSource = "Pekao",
              name = Option(counterparty).filter(_.nonEmpty).orElse(Option(title).filter(_.nonEmpty)).getOrElse("Nieopisany wydatek Pekao").take(255),
              note = bankNote("Pekao", fileName, rowNumber, title, transactionType),
              amount = -sourceAmount,
              currency = selectedCurrency,
              expenseDate = selectedDate,
              entryType = "expense",
              fingerprint = fingerprint
            )
          case (Some(selectedDate), Some(sourceAmount), Some(selectedCurrency)) if sourceAmount > 0 && transactionType.toUpperCase.startsWith("ZWROT") =>
            val fingerprint = sha256(
              List(
                "pekao-csv",
                reference,
                selectedDate,
                valueDate.getOrElse(selectedDate),
                sourceAmount.bigDecimal.toPlainString,
                selectedCurrency,
                counterparty,
                title,
                transactionType
              ).mkString("|").toLowerCase
            )
            parsedRows += bankImportRow(
              rowNumber = rowNumber,
              paymentSource = "Pekao",
              name = Option(counterparty).filter(_.nonEmpty).getOrElse("Zwrot Pekao").take(255),
              note = bankNote("Pekao", fileName, rowNumber, title, transactionType),
              amount = -sourceAmount,
              currency = selectedCurrency,
              expenseDate = selectedDate,
              entryType = "refund",
              fingerprint = fingerprint
            )
          case (_, Some(sourceAmount), _) if ownTransfer && sourceAmount < 0 => skippedOwnTransfers += 1
          case (_, Some(sourceAmount), _) if sourceAmount >= 0               => skippedIncomes += 1
          case _                                                             => skippedInvalid += 1
        }
      }
    } finally csvParser.close()

    ExpenseImportPreview(
      format = "pekao-csv",
      fileName = fileName,
      checksum = sha256(content),
      rows = parsedRows.toList,
      warnings = skippedWarnings(skippedIncomes, skippedOwnTransfers, 0, 0, skippedInvalid, 0)
    )
  }

  private def parseNativeJson(fileName: String, content: Array[Byte]): ExpenseImportPreview = {
    val json   = parse(String(content, StandardCharsets.UTF_8)).fold(throw _, identity)
    val format = json.hcursor.get[String]("format").fold(throw _, identity)
    if (format != "my-funds-expenses-v1") {
      throw new IllegalArgumentException("Nieobsługiwana wersja natywnego JSON")
    }
    val paymentSourceTypes = json.hcursor
      .get[List[Json]]("sources")
      .getOrElse(List.empty)
      .flatMap { paymentSource =>
        for {
          name              <- paymentSource.hcursor.get[String]("name").toOption
          paymentSourceType <- paymentSource.hcursor.get[String]("type").toOption
        } yield name.toLowerCase -> paymentSourceType
      }
      .toMap
    val categoryConfiguration = json.hcursor
      .get[List[Json]]("categories")
      .getOrElse(List.empty)
      .flatMap { category =>
        category.hcursor.get[String]("name").toOption.map { name =>
          name.toLowerCase -> (
            category.hcursor.get[String]("color").getOrElse("#78716C"),
            category.hcursor.get[String]("icon").getOrElse("ellipsis")
          )
        }
      }
      .toMap
    val rows = json.hcursor.get[List[Json]]("items").fold(throw _, identity).zipWithIndex.map { case (item, index) =>
      val amount        = item.hcursor.get[BigDecimal]("amount").fold(throw _, identity)
      val expenseMonth  = LocalDate.parse(item.hcursor.get[String]("month").fold(throw _, identity))
      val paymentSource = item.hcursor.get[String]("source").toOption.filter(_.trim.nonEmpty)
      val name          = item.hcursor.get[String]("name").fold(throw _, identity)
      val currency      = item.hcursor.get[String]("currency").getOrElse("PLN")
      ExpenseImportRow(
        rowNumber = index + 1,
        folderPath = item.hcursor.get[String]("folder").toOption.filter(_.trim.nonEmpty),
        paymentSource = paymentSource,
        category = item.hcursor.get[String]("category").toOption.filter(_.trim.nonEmpty),
        tags = item.hcursor.get[List[String]]("tags").getOrElse(List.empty),
        name = name,
        note = item.hcursor.get[String]("note").toOption.filter(_.trim.nonEmpty),
        amount = amount,
        currency = currency,
        expenseMonth = expenseMonth,
        expenseDate = item.hcursor.get[String]("date").toOption.filter(_.trim.nonEmpty).map(LocalDate.parse),
        entryType = item.hcursor.get[String]("type").getOrElse(if (amount < 0) "refund" else "expense"),
        fingerprint = item.hcursor
          .get[String]("fingerprint")
          .getOrElse(
            sha256(List("json", expenseMonth, paymentSource.getOrElse("missing"), name, amount, currency, index + 1).mkString("|").toLowerCase)
          ),
        warnings = if (paymentSource.isEmpty) List("Brak źródła płatności — wybierz je przed zatwierdzeniem") else List.empty,
        paymentSourceType = paymentSource.flatMap(value => paymentSourceTypes.get(value.toLowerCase)),
        categoryColor = item.hcursor.get[String]("category").toOption.flatMap(value => categoryConfiguration.get(value.toLowerCase).map(_._1)),
        categoryIcon = item.hcursor.get[String]("category").toOption.flatMap(value => categoryConfiguration.get(value.toLowerCase).map(_._2)),
        countingStatus = item.hcursor.get[String]("countingStatus").getOrElse("included")
      )
    }
    ExpenseImportPreview("native-json", fileName, sha256(content), rows, List.empty)
  }

  private def parseNativeCsv(fileName: String, content: Array[Byte]): ExpenseImportPreview = {
    val csvFormat = CSVFormat.DEFAULT.builder().setHeader().setSkipHeaderRecord(true).setIgnoreEmptyLines(true).build()
    val csvParser = new CSVParser(new InputStreamReader(new ByteArrayInputStream(content), StandardCharsets.UTF_8), csvFormat)
    try {
      val rows = csvParser
        .iterator()
        .asScala
        .zipWithIndex
        .map { case (record, index) =>
          val amount        = BigDecimal(record.get("amount").replace(" ", "").replace(" ", "").replace(",", "."))
          val expenseMonth  = LocalDate.parse(record.get("month"))
          val paymentSource = Option(record.get("source")).map(_.trim).filter(_.nonEmpty)
          val name          = record.get("name").trim
          val currency      = Option(record.get("currency")).map(_.trim).filter(_.nonEmpty).getOrElse("PLN")
          ExpenseImportRow(
            rowNumber = index + 2,
            folderPath = Option(record.get("folder")).map(_.trim).filter(_.nonEmpty),
            paymentSource = paymentSource,
            category = Option(record.get("category")).map(_.trim).filter(_.nonEmpty),
            tags = Option(record.get("tags")).map(_.split(";").toList.map(_.trim).filter(_.nonEmpty)).getOrElse(List.empty),
            name = name,
            note = Option(record.get("note")).map(_.trim).filter(_.nonEmpty),
            amount = amount,
            currency = currency,
            expenseMonth = expenseMonth,
            expenseDate = Option(record.get("date")).map(_.trim).filter(_.nonEmpty).map(LocalDate.parse),
            entryType = Option(record.get("type")).map(_.trim).filter(_.nonEmpty).getOrElse(if (amount < 0) "refund" else "expense"),
            countingStatus = if (record.isMapped("countingStatus")) Option(record.get("countingStatus")).map(_.trim).filter(_.nonEmpty).getOrElse("included") else "included",
            fingerprint = Option(record.get("fingerprint"))
              .map(_.trim)
              .filter(_.nonEmpty)
              .getOrElse(
                sha256(List("csv", expenseMonth, paymentSource.getOrElse("missing"), name, amount, currency, index + 1).mkString("|").toLowerCase)
              ),
            warnings = if (paymentSource.isEmpty) List("Brak źródła płatności — wybierz je przed zatwierdzeniem") else List.empty
          )
        }
        .toList
      ExpenseImportPreview("native-csv", fileName, sha256(content), rows, List.empty)
    } finally csvParser.close()
  }

  private def bankImportRow(
    rowNumber: Int,
    paymentSource: String,
    name: String,
    note: String,
    amount: BigDecimal,
    currency: String,
    expenseDate: LocalDate,
    entryType: String,
    fingerprint: String,
    warnings: List[String] = List.empty
  ): ExpenseImportRow =
    ExpenseImportRow(
      rowNumber = rowNumber,
      folderPath = Some(s"${expenseDate.getYear}/${monthNames(expenseDate.getMonthValue - 1)}"),
      paymentSource = Some(paymentSource),
      category = None,
      tags = List.empty,
      name = name,
      note = Some(note),
      amount = amount,
      currency = currency.toUpperCase,
      expenseMonth = expenseDate.withDayOfMonth(1),
      expenseDate = Some(expenseDate),
      entryType = entryType,
      fingerprint = fingerprint,
      warnings = warnings,
      paymentSourceType = Some("bank"),
      countingStatus = {
        val normalizedName = normalizedText(name).toLowerCase
        val normalizedNote = normalizedText(note).toLowerCase
        if (
          normalizedNote.contains("typ operacji: przelew do us") ||
          normalizedNote.contains("typ operacji: przelew podzielony do zus/krus") ||
          normalizedName == "usługi księgowe"
        ) {
          "excluded_business"
        } else if (
          normalizedName.contains("transfer własny") ||
          normalizedName.contains("przelew własny")
        ) {
          "excluded_internal_transfer"
        } else if (
          (normalizedName == "mbank" && normalizedNote.contains("ike")) ||
          normalizedName.contains("pko biuro maklerskie") ||
          normalizedNote.contains("obligacj") ||
          normalizedName.contains("okx") ||
          normalizedNote.contains("okx")
        ) {
          "excluded_investment"
        } else if (normalizedNote.contains("typ operacji: wypłata kartą")) {
          "excluded_cash_transfer"
        } else if (
          normalizedName.startsWith("zwrot kosztów") &&
          normalizedNote.contains("zwrot kosztów")
        ) {
          "excluded_reimbursed"
        } else {
          "included"
        }
      }
    )

  private def bankNote(source: String, fileName: String, rowNumber: Int, description: String, transactionType: String): String =
    List(
      Some(s"Import bankowy $source z $fileName, wiersz $rowNumber"),
      Option(normalizedText(description)).filter(_.nonEmpty),
      Option(normalizedText(transactionType)).filter(_.nonEmpty).map(value => s"Typ operacji: $value")
    ).flatten.mkString("; ")

  private def skippedWarnings(
    skippedIncomes: Int,
    skippedOwnTransfers: Int,
    skippedExchanges: Int,
    skippedUnfinished: Int,
    skippedInvalid: Int,
    nonZeroFees: Int
  ): List[String] =
    List(
      Option.when(skippedIncomes > 0)(s"Pominięto $skippedIncomes wpływów niebędących zwrotami"),
      Option.when(skippedOwnTransfers > 0)(s"Pominięto $skippedOwnTransfers przelewów pomiędzy własnymi rachunkami"),
      Option.when(skippedExchanges > 0)(s"Pominięto $skippedExchanges operacji wymiany walut"),
      Option.when(skippedUnfinished > 0)(s"Pominięto $skippedUnfinished operacji niezakończonych lub cofniętych"),
      Option.when(skippedInvalid > 0)(s"Pominięto $skippedInvalid niekompletnych wierszy"),
      Option.when(nonZeroFees > 0)(s"W $nonZeroFees operacjach występuje osobna opłata bankowa; sprawdź ostrzeżenia wierszy")
    ).flatten

  private def meaningfulName(value: String): Option[String] =
    value.linesIterator
      .map(_.trim)
      .filter(_.nonEmpty)
      .filterNot(line => line.replaceAll("[^0-9]", "").length >= 18)
      .map(normalizedText)
      .find(_.nonEmpty)

  private def normalizedText(value: String): String = value.replaceAll("\\s+", " ").trim

  private def cellText(cell: Cell, dataFormatter: DataFormatter, formulaEvaluator: FormulaEvaluator): String = dataFormatter.formatCellValue(cell, formulaEvaluator).trim

  private def cellAmount(cell: Cell, dataFormatter: DataFormatter, formulaEvaluator: FormulaEvaluator): Option[BigDecimal] =
    if (cell.getCellType == CellType.NUMERIC) {
      Some(BigDecimal(cell.getNumericCellValue.toString))
    } else if (cell.getCellType == CellType.FORMULA && formulaEvaluator.evaluate(cell).getCellType == CellType.NUMERIC) {
      Some(BigDecimal(formulaEvaluator.evaluate(cell).getNumberValue.toString))
    } else {
      Try(
        dataFormatter
          .formatCellValue(cell, formulaEvaluator)
          .replace(" ", "")
          .replace(" ", "")
          .replace(",", ".")
          .trim
      ).toOption.filter(_.nonEmpty).flatMap(value => Try(BigDecimal(value)).toOption)
    }

  private def cellDate(cell: Cell, dataFormatter: DataFormatter, formulaEvaluator: FormulaEvaluator): Option[LocalDate] =
    if (cell.getCellType == CellType.NUMERIC) {
      Try(DateUtil.getLocalDateTime(cell.getNumericCellValue).toLocalDate).toOption
    } else if (cell.getCellType == CellType.FORMULA && formulaEvaluator.evaluate(cell).getCellType == CellType.NUMERIC) {
      Try(DateUtil.getLocalDateTime(formulaEvaluator.evaluate(cell).getNumberValue).toLocalDate).toOption
    } else {
      val formattedValue = dataFormatter.formatCellValue(cell, formulaEvaluator).trim
      List(
        Try(LocalDate.parse(formattedValue)).toOption,
        Try(LocalDate.parse(formattedValue, polishDateFormatter)).toOption
      ).flatten.headOption
    }

  private def sha256(value: String): String = sha256(value.getBytes(StandardCharsets.UTF_8))

  private def sha256(value: Array[Byte]): String = MessageDigest.getInstance("SHA-256").digest(value).map(byte => f"$byte%02x").mkString
}
