package dawidkobak.myfunds.transaction.handler

import dawidkobak.myfunds.domain.DatabaseAccess
import dawidkobak.myfunds.transaction.Transaction
import doobie.implicits.*
import io.circe.*
import zio.*
import zio.interop.catz.*

object TransactionUpdateHandler {
  def apply(payload: Json, owner: String): RIO[DatabaseAccess, Json] =
    for {
      databaseAccess <- ZIO.service[DatabaseAccess]
      id               <- ZIO.fromEither(payload.hcursor.get[Int]("id"))
      walletId         <- ZIO.fromEither(payload.hcursor.downField("wallet").get[Int]("id")).option
      amount           <- ZIO.fromEither(payload.hcursor.get[Double]("amount")).option
      stockId          <- ZIO.fromEither(payload.hcursor.downField("stock").get[Int]("id")).option
      purchaseCost     <- ZIO.fromEither(payload.hcursor.get[Double]("purchaseCost")).option
      purchaseDate     <- ZIO.fromEither(payload.hcursor.get[String]("purchaseDate")).option
      transactionType  <- ZIO.fromEither(payload.hcursor.get[String]("transactionType")).option
      asset <-
        sql"""
               WITH updated_transaction AS (
                 UPDATE mf_transaction SET
                   wallet_id = ${if (walletId.isDefined) walletId.get else fr"""wallet_id"""},
                   amount = ${if (amount.isDefined) amount.get else fr"""amount"""},
                   stock_id = ${if (stockId.isDefined) stockId.get else fr"""stock_id"""},
                   purchasecost = ${if (purchaseCost.isDefined) purchaseCost.get else fr"""purchasecost"""},
                   purchaseDate = ${if (purchaseDate.isDefined) purchaseDate.get else fr"""purchaseDate"""}::date,
                   transactionType = ${if (transactionType.isDefined) transactionType.get else fr"""transactionType"""}
                 WHERE id = $id
                 RETURNING id, wallet_id, amount, purchaseCost, purchaseDate, stock_id, transactionType
               )
               SELECT t.id, w.id, w.name, t.amount, t.purchaseCost, t.purchaseDate, s.id, s.name, s.color, t.transactionType
               FROM updated_transaction t
               JOIN mf_stock s on t.stock_id = s.id
               JOIN mf_wallet w on t.wallet_id = w.id
           """
          .query[(Int, Int, String, Double, Double, String, Int, String, Option[String], String)]
          .unique
          .transact(databaseAccess.transactor)
    } yield Json.obj(
      "id" -> Json.fromInt(asset._1),
      "wallet" -> Json.obj(
        "id"   -> Json.fromInt(asset._2),
        "name" -> Json.fromString(asset._3)
      ),
      "amount"       -> Json.fromDoubleOrNull(asset._4),
      "purchaseCost" -> Json.fromDoubleOrNull(asset._5),
      "purchaseDate" -> Json.fromString(asset._6),
      "stock" -> Json.obj(
        "id"    -> Json.fromInt(asset._7),
        "name"  -> Json.fromString(asset._8),
        "color" -> asset._9.fold(Json.Null)(Json.fromString)
      ),
      "transactionType" -> Json.fromString(asset._10)
    )
}
