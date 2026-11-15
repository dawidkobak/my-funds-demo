package dawidkobak.myfunds.subexpense

case class SubExpense(
  id: Int,
  name: String,
  createdAt: String,
  value: Double,
  expenseId: Int
)
