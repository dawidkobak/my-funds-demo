package dawidkobak.myfunds.wallet.handler

import dawidkobak.myfunds.domain.DatabaseAccess
import dawidkobak.myfunds.wallet.Wallet
import doobie.implicits.*
import io.circe.*
import zio.*
import zio.interop.catz.*

object WalletUpdateHandler {
  def apply(payload: Json, ownerName: String): RIO[DatabaseAccess, Json] =
    for {
      databaseAccess <- ZIO.service[DatabaseAccess]
      id               <- ZIO.fromEither(payload.hcursor.get[Int]("id"))
      name             <- ZIO.fromEither(payload.hcursor.get[String]("name")).option
      walletType       <- ZIO.fromEither(payload.hcursor.get[String]("walletType")).option
      wallet <-
        sql"""
              UPDATE mf_wallet SET
                name = ${if (name.isDefined) name.get else fr"""name"""},
                wallet_type = ${if (walletType.isDefined) walletType.get else fr"""wallet_type"""}
              WHERE id = $id AND owner_id = (SELECT id FROM mf_user WHERE username = $ownerName)
              RETURNING id, name, owner_id, wallet_type;
           """
          .query[Wallet]
          .unique
          .transact(databaseAccess.transactor)
    } yield Json.obj(
      "id"         -> Json.fromInt(wallet.id),
      "name"       -> Json.fromString(wallet.name),
      "walletType" -> Json.fromString(wallet.walletType)
    )
}
