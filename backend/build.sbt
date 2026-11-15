ThisBuild / scalaVersion       := "3.10.1"
ThisBuild / crossScalaVersions := Seq("3.10.1")

Global / onChangedBuildSource := ReloadOnSourceChanges

// Compilation performance optimizations
ThisBuild / parallelExecution := true

// Enable incremental compilation optimizations
ThisBuild / turbo := true

// JVM settings for better compilation performance
ThisBuild / Compile / fork := true
ThisBuild / Test / fork := true

// HTTP API sbt project
lazy val api = (project in file("api"))
  .settings(
    name         := "api",
    scalaVersion := "3.10.1",
    target := {
      (ThisBuild / baseDirectory).value / "../.bsp" / "target" / thisProject.value.id
    },

    // Set a custom Coursier cache directory for the api project
    csrCacheDirectory := baseDirectory.value / "../.bsp" / "api" / "coursier",
    // Set a custom Ivy cache directory for the api project
    ivyPaths := IvyPaths(baseDirectory.value / "../.bsp", Some(baseDirectory.value / "api" / "ivy2")),

    // Main class
    Compile / mainClass := Some(Option(System.getenv("SBT_MAIN_CLASS")).getOrElse("dawidkobak.Application")),

    // Publish only the market-data CLI; retain other local commands outside the build.
    Compile / unmanagedSources := {
      val localCliDirectory = baseDirectory.value / "src/main/scala/dawidkobak/myfunds/cli"
      val publishedCliSources = Set(
        "CliApplication.scala",
        "model/CommandDefinition.scala",
        "model/CommandTree.scala",
        "helper/ErrorUtils.scala",
        "currency/ImportCurrencyRatesCommand.scala",
        "stock/ImportYahooNbpQuotesCommand.scala",
        "stock/YahooNbpImportHelper.scala"
      )
      (Compile / unmanagedSources).value.filter { source =>
        !source.toPath.startsWith(localCliDirectory.toPath) ||
          publishedCliSources.contains(localCliDirectory.toPath.relativize(source.toPath).toString)
      }
    },

    // Compilation optimizations for API project
    Compile / maxErrors := 10,
    scalacOptions ++= Seq(
      "-feature",
      "-deprecation",
      "-unchecked"
    ),

    // Keep the stable JSON runtime aligned across dependencies.
    dependencyOverrides += "dev.zio" %% "zio-json" % "1.0.0",

    // Main app dependencies
    libraryDependencies ++= globalDependencies,
    // Resources folder management
    Compile / unmanagedResourceDirectories := Seq(
      baseDirectory.value / "src" / "main" / "resources",
      baseDirectory.value / "src" / "main" / "resources" / Option(System.getenv("ENVIRONMENT")).getOrElse("development")
    )
  )

val globalDependencies = Seq(
  // ZIO
  "dev.zio"                       %% "zio"                % "2.1.27",
  "dev.zio"                       %% "zio-streams"        % "2.1.27",
  "dev.zio"                       %% "zio-cli"            % "0.8.2",
  "dev.zio"                       %% "zio-logging-slf4j2" % "2.5.4",
  "dev.zio"                       %% "zio-http"           % "3.11.6",
  "dev.zio"                       %% "zio-interop-cats"   % "23.1.0.15",
  "org.typelevel"                 %% "cats-effect"        % "3.7.1",
  "com.softwaremill.sttp.client3" %% "zio"                % "3.11.0",
  "com.softwaremill.sttp.client3" %% "async-http-client-backend-zio" % "3.11.0",

  // Doobie
  "org.tpolecat" %% "doobie-core" % "1.0.0-RC12",
  "org.tpolecat" %% "doobie-hikari" % "1.0.0-RC12",
  "org.tpolecat" %% "doobie-postgres" % "1.0.0-RC12",

  // Circe
  "io.circe" %% "circe-core"    % "0.14.17",
  "io.circe" %% "circe-generic" % "0.14.17",
  "io.circe" %% "circe-parser"  % "0.14.17",

  "com.typesafe" % "config" % "1.4.10",


  // Database migrations
  "org.flywaydb" % "flyway-core" % "13.12.2",
  "org.flywaydb" % "flyway-database-postgresql" % "13.12.2",


  // Logs
  "ch.qos.logback" % "logback-classic" % "1.6.7",
  "ch.qos.logback" % "logback-core"    % "1.6.7",
  "org.slf4j"      % "slf4j-api"       % "2.0.20",


  // HTTP expense imports
  "org.apache.poi"           % "poi"            % "6.0.0",
  "org.apache.poi"           % "poi-ooxml"      % "6.0.0",
  "org.apache.commons"       % "commons-csv"    % "1.14.1",
  "org.apache.logging.log4j" % "log4j-to-slf4j" % "2.26.1",
)
