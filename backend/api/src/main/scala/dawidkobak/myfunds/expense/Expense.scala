package dawidkobak.myfunds.expense

case class Expense(
  id: Int,
  name: String,
  value: Double,
  createdAt: String,
  expenseList: Int
)
