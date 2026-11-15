package dawidkobak.myfunds.domain

import java.lang.System
import java.sql.SQLTransientConnectionException
import scala.concurrent.ExecutionContext

import cats.effect.kernel.{Async, Resource}
import com.typesafe.config.{Config, ConfigFactory}
import doobie.hikari.HikariTransactor
import zio.*
import zio.interop.catz.*

object DatabaseAccess {
  val settings: Config = ConfigFactory.load()

  def openTransactor(executionContext: ExecutionContext, poolSize: Int = 10, timeout: Int = 30): Resource[Task, HikariTransactor[Task]] = {
    val jdbcUrl = s"jdbc:postgresql://${settings.getString("pgsql.host")}:${settings.getString("pgsql.port")}/${settings.getString("pgsql.database")}"

    for {
      connectionPool <- HikariTransactor.newHikariTransactor[Task](
        "org.postgresql.Driver",
        jdbcUrl,
        settings.getString("pgsql.username"),
        System.getenv("PGSQL_DB_PASSWORD"),
        executionContext
      )
      _ <- Resource.eval(connectionPool.configure { dataSource =>
        Async[Task].delay {
          dataSource.setMaximumPoolSize(poolSize)
          dataSource.setAutoCommit(false)
          dataSource.setConnectionTimeout(timeout * 1000)
        }
      })
    } yield connectionPool
  }

  def layer: TaskLayer[DatabaseAccess] =
    ZLayer.scoped {
      ZIO.acquireRelease {
        for {
          blockingExecutor <- ZIO.blockingExecutor
          connectionPool <- openTransactor(blockingExecutor.asExecutionContext, poolSize = 10, timeout = 30)
            .allocated.map(_._1)
        } yield connectionPool
      } { connectionPool =>
        ZIO.succeed(connectionPool.kernel.close())
      }.retry(
        Schedule.recurs(3) && Schedule.recurWhile[Throwable] {
          case _: SQLTransientConnectionException => true
          case _                                  => false
        }
      ).map(connectionPool => new DatabaseAccess(connectionPool))
    }
}

class DatabaseAccess(val transactor: HikariTransactor[Task])
