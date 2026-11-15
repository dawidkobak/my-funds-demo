package dawidkobak.myfunds.piggybank.handler

import dawidkobak.myfunds.domain.DatabaseAccess
import doobie.implicits.*
import io.circe.*
import zio.*
import zio.interop.catz.*

object PiggyBankDeleteHandler {
  def apply(piggyBankId: Int): RIO[DatabaseAccess, Json] =
    for {
      databaseAccess <- ZIO.service[DatabaseAccess]
      _ <-
        sql"""
              DELETE FROM mf_piggy_bank WHERE id = $piggyBankId
           """.update.run
          .transact(databaseAccess.transactor)
    } yield Json.True
}
