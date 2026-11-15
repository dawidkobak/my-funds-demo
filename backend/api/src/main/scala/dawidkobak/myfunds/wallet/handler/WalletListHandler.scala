package dawidkobak.myfunds.wallet.handler

import dawidkobak.myfunds.domain.DatabaseAccess
import dawidkobak.myfunds.wallet.Wallet
import doobie.implicits.*
import io.circe.*
import zio.*
import zio.interop.catz.*

object WalletListHandler {
  def apply(walletType: Option[String]): RIO[DatabaseAccess, Json] =
    for {
      databaseAccess <- ZIO.service[DatabaseAccess]
      wallets <-
        sql"""
              SELECT id, name, owner_id, wallet_type FROM mf_wallet
              WHERE ${walletType.fold(fr"""true""")(walletType => fr"mf_wallet.wallet_type = $walletType")}
              ORDER BY id
           """
          .query[Wallet]
          .to[List]
          .transact(databaseAccess.transactor)
    } yield Json.fromValues(
      wallets.map(wallet =>
        Json.obj(
          "id"         -> Json.fromInt(wallet.id),
          "name"       -> Json.fromString(wallet.name),
          "walletType" -> Json.fromString(wallet.walletType)
        )
      )
    )
}
