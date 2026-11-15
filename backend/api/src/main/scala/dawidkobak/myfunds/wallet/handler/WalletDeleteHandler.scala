package dawidkobak.myfunds.wallet.handler

import dawidkobak.myfunds.domain.DatabaseAccess
import doobie.implicits.*
import io.circe.*
import zio.*
import zio.interop.catz.*

object WalletDeleteHandler {
  def apply(walletId: Int): RIO[DatabaseAccess, Json] =
    for {
      databaseAccess <- ZIO.service[DatabaseAccess]
      _ <-
        sql"""
              DELETE FROM mf_wallet WHERE id = $walletId
           """.update.run
          .transact(databaseAccess.transactor)
    } yield Json.True
}
