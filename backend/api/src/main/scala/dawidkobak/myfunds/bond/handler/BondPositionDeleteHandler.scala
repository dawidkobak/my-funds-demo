package dawidkobak.myfunds.bond.handler

import dawidkobak.myfunds.domain.DatabaseAccess
import doobie.implicits.*
import io.circe.Json
import zio.*
import zio.interop.catz.*

object BondPositionDeleteHandler {
  def apply(bondPositionId: Int): RIO[DatabaseAccess, Json] =
    for {
      databaseAccess <- ZIO.service[DatabaseAccess]
      _ <-
        sql"""
              DELETE FROM mf_bond_position WHERE id = $bondPositionId
           """.update.run
          .transact(databaseAccess.transactor)
    } yield Json.obj("deleted" -> Json.fromInt(bondPositionId))
}
