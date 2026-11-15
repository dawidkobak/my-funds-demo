package dawidkobak.myfunds.transaction

case class Transaction(
  id: Int,
  walletId: Int,
  amount: Double,
  purchaseCost: Double,
  purchaseDate: String,
  stockId: Option[Int],
  color: Option[String],
  transactionType: String
)
