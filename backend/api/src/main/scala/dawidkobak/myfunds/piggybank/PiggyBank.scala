package dawidkobak.myfunds.piggybank

case class PiggyBank(
  id: Int,
  name: String,
  aimAmount: Double,
  currentAmount: Double,
  dueDate: String,
  link: Option[String]
)
