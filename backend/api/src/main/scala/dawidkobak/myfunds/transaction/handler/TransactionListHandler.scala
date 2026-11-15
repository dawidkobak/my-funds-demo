package dawidkobak.myfunds.transaction.handler

import dawidkobak.myfunds.domain.DatabaseAccess
import dawidkobak.myfunds.transaction.Transaction
import doobie.implicits.*
import io.circe.*
import zio.*
import zio.interop.catz.*

object TransactionListHandler {
  def apply(walletIdQP: Option[Int]): RIO[DatabaseAccess, Json] =
    for {
      databaseAccess <- ZIO.service[DatabaseAccess]
      assets <-
        sql"""
              SELECT t.id, w.id, w.name, amount, purchaseCost, purchaseDate, s.id, s.name, s.color, t.transactionType
              FROM mf_transaction t
              JOIN mf_stock s on t.stock_id = s.id
              JOIN mf_wallet w on t.wallet_id = w.id
              WHERE ${walletIdQP.fold(fr"""true""")(walletId => fr"w.id = $walletId")}
           """
          .query[(Int, Int, String, Double, Double, String, Int, String, Option[String], String)]
          .to[List]
          .transact(databaseAccess.transactor)
    } yield Json.fromValues(
      assets.map { case (assetId, walletId, walletName, amount, purchaseCost, purchaseDate, stockId, stockName, color, transactionType) =>
        Json.obj(
          "id" -> Json.fromInt(assetId),
          "wallet" -> Json.obj(
            "id"   -> Json.fromInt(walletId),
            "name" -> Json.fromString(walletName)
          ),
          "amount" -> Json.fromDoubleOrNull(amount),
          "stock" -> Json.obj(
            "id"    -> Json.fromInt(stockId),
            "name"  -> Json.fromString(stockName),
            "color" -> color.fold(Json.Null)(Json.fromString)
          ),
          "purchaseCost"    -> Json.fromDoubleOrNull(purchaseCost),
          "purchaseDate"    -> Json.fromString(purchaseDate),
          "transactionType" -> Json.fromString(transactionType)
        )
      }
    )
}
