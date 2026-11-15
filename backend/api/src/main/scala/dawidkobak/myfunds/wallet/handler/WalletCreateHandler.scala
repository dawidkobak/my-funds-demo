package dawidkobak.myfunds.wallet.handler

import dawidkobak.myfunds.domain.DatabaseAccess
import dawidkobak.myfunds.wallet.Wallet
import doobie.implicits.*
import io.circe.*
import zio.*
import zio.interop.catz.*

object WalletCreateHandler {
  def apply(payload: Json, ownerName: String): RIO[DatabaseAccess, Json] =
    for {
      databaseAccess <- ZIO.service[DatabaseAccess]
      name             <- ZIO.fromEither(payload.hcursor.get[String]("name"))
      walletType       <- ZIO.fromEither(payload.hcursor.get[String]("walletType")).option
      wallet <-
        sql"""
              INSERT INTO mf_wallet (owner_id, name, wallet_type) VALUES (
                 (SELECT id FROM mf_user WHERE username = $ownerName),
                 $name,
                 ${walletType.getOrElse("STOCK")}
              )
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
