package dawidkobak.myfunds.expenses

import java.text.Normalizer
import java.time.LocalDate
import java.util.Locale

final case class ExpenseAutomaticClassification(categoryName: Option[String], tagNames: List[String], description: Option[String])

object ExpenseAutomaticClassification {
  def classify(
    name: String,
    note: Option[String],
    amount: BigDecimal,
    expenseMonth: LocalDate,
    paymentSourceName: Option[String]
  ): ExpenseAutomaticClassification = {
    val normalizedText = Normalizer
      .normalize(List(name, note.getOrElse("")).mkString(" "), Normalizer.Form.NFD)
      .replaceAll("\\p{M}+", "")
      .toLowerCase(Locale.ROOT)
      .replace('ł', 'l')
    val categoryName =
      if (List("biedronka", "lidl", "kaufland", "zabka", "spozywcz", "piekarnia").exists(normalizedText.contains)) Some("Zakupy spożywcze")
      else if (List("restauracja", "kebab", "uber eats", "kfc", "mcdonald").exists(normalizedText.contains)) Some("Jedzenie na mieście")
      else if (List("orlen", "paliwo", "parking", "bilet autobus", "uber").exists(normalizedText.contains)) Some("Transport")
      else if (List("czynsz", "energia elektryczna", "mieszkanie").exists(normalizedText.contains)) Some("Mieszkanie i dom")
      else if (List("netflix", "spotify", "youtube", "subskrypcj").exists(normalizedText.contains)) Some("Subskrypcje")
      else if (List("apteka", "lekarz", "dentysta").exists(normalizedText.contains)) Some("Zdrowie")
      else if (List("kino", "muzeum", "koncert").exists(normalizedText.contains)) Some("Rozrywka")
      else if (List("ike", "bitcoin", "xtb", "obligacj").exists(normalizedText.contains)) Some("Inwestycje")
      else None
    ExpenseAutomaticClassification(categoryName, Nil, None)
  }
}
