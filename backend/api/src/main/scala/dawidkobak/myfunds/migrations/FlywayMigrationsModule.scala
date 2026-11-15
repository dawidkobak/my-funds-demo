package dawidkobak.myfunds.migrations

import com.typesafe.config.ConfigFactory
import org.flywaydb.core.Flyway
import zio.*

object FlywayMigrationsModule {
  def migrate: Task[Unit] =
    for {
      configuration <- ZIO.attempt(ConfigFactory.load())
      migrationResult <- ZIO.attemptBlocking {
        Flyway
          .configure()
          .dataSource(
            s"jdbc:postgresql://${configuration.getString("pgsql.host")}:${configuration.getString("pgsql.port")}/${configuration.getString("pgsql.database")}",
            configuration.getString("pgsql.username"),
            java.lang.System.getenv("PGSQL_DB_PASSWORD")
          )
          .locations("classpath:db/migration")
          .cleanDisabled(true)
          .load()
          .migrate()
      }
      _ <- ZIO.logInfo(s"Flyway migrations applied: ${migrationResult.migrationsExecuted}")
    } yield ()
}
