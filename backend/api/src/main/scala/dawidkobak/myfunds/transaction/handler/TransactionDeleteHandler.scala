package dawidkobak.myfunds.transaction.handler

import dawidkobak.myfunds.domain.DatabaseAccess
import doobie.implicits.*
import io.circe.*
import zio.*
import zio.interop.catz.*

object TransactionDeleteHandler {
  def apply(transactionId: Int): RIO[DatabaseAccess, Json] =
    for {
      databaseAccess <- ZIO.service[DatabaseAccess]
      _ <-
        sql"""
              DELETE FROM mf_transaction WHERE id = $transactionId
           """.update.run
          .transact(databaseAccess.transactor)
    } yield Json.True
}
