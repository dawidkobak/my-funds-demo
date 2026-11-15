package dawidkobak.myfunds.transaction.handler

import dawidkobak.myfunds.domain.DatabaseAccess
import dawidkobak.myfunds.transaction.Transaction
import doobie.implicits.*
import io.circe.*
import zio.*
import zio.interop.catz.*

object TransactionCreateHandler {
  def apply(payload: Json, owner: String): RIO[DatabaseAccess, Json] =
    for {
      databaseAccess <- ZIO.service[DatabaseAccess]
      walletId         <- ZIO.fromEither(payload.hcursor.downField("wallet").get[Int]("id"))
      amount           <- ZIO.fromEither(payload.hcursor.get[Double]("amount"))
      stockId          <- ZIO.fromEither(payload.hcursor.downField("stock").get[Int]("id")).option
      purchaseCost     <- ZIO.fromEither(payload.hcursor.get[Double]("purchaseCost"))
      purchaseDate     <- ZIO.fromEither(payload.hcursor.get[String]("purchaseDate"))
      transactionType  <- ZIO.fromEither(payload.hcursor.get[String]("transactionType"))
      asset <-
        sql"""
               WITH new_transaction AS (
                 INSERT INTO mf_transaction (wallet_id, amount, stock_id, purchaseCost, purchaseDate, transactionType) VALUES (
                   $walletId,
                   $amount,
                   $stockId,
                   $purchaseCost,
                   $purchaseDate::date,
                   $transactionType
                 )
                 RETURNING *
               )
               SELECT t.id, w.id, w.name, t.amount, t.purchaseCost, t.purchaseDate, s.id, s.name, s.color, t.transactionType
               FROM new_transaction t
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
      "amount" -> Json.fromDoubleOrNull(asset._4),
      "stock" -> Json.obj(
        "id"    -> Json.fromInt(asset._7),
        "name"  -> Json.fromString(asset._8),
        "color" -> asset._9.fold(Json.Null)(Json.fromString)
      ),
      "purchaseCost"    -> Json.fromDoubleOrNull(asset._5),
      "purchaseDate"    -> Json.fromString(asset._6),
      "transactionType" -> Json.fromString(asset._10)
    )
}
