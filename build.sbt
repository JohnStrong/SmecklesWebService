import sbt.Defaults

ThisBuild / scalaVersion := "3.3.7"   // use a recent stable Scala 3 version
ThisBuild / version := "0.1.0"
ThisBuild / organization := "org.myapps.shoppinglistservice"

lazy val FunctionalTest = config("functional") extend Test

lazy val root = (project in file("."))
  .enablePlugins(PlayScala)
  .configs(FunctionalTest)
  .settings(
    name := "SimpleShoppingListApp",
    // Jackson version alignment: Pekko pulls jackson-databind 2.15.0 but jackson-module-scala
    // (transitive via Pekko serialization) requires 2.14.x. Without this override, the app
    // fails at runtime with: "Scala module 2.14.3 requires Jackson Databind version >= 2.14.0 and < 2.15.0"
    dependencyOverrides ++= Seq(
      "com.fasterxml.jackson.core" % "jackson-databind" % "2.14.3",
      "com.fasterxml.jackson.core" % "jackson-core" % "2.14.3",
      "com.fasterxml.jackson.core" % "jackson-annotations" % "2.14.3"
    ),
    libraryDependencies ++= Seq(
      guice,
      "org.scalatest" %% "scalatest" % "3.2.20" % Test,
      "org.scalatestplus.play" %% "scalatestplus-play" % "7.0.1" % Test,
      "org.mockito" % "mockito-core" % "5.22.0" % Test,
      "org.playframework" %% "play-slick"            % "6.1.1",     // object-relational mapping
      "org.playframework" %% "play-slick-evolutions" % "6.1.1",     // Slick + Evolutions
      "com.h2database" % "h2" % "2.2.224",                          // H2 driver (dev db)
      "org.postgresql"     % "postgresql"            % "42.7.3",    // production driver (postgres)
      evolutions,                                                    // Play Evolutions for schema management
      "com.auth0" % "java-jwt" % "4.4.0",   // auth: JWT decode + verify
      "com.auth0" % "jwks-rsa" % "0.22.1"   // auth: Fetches Google's public signing keys
    ),
    // Functional test configuration - run with sbt functional:test
    inConfig(FunctionalTest)(Defaults.testSettings),

    FunctionalTest / sourceDirectory := baseDirectory.value / "functional-tests",
    FunctionalTest / scalaSource := baseDirectory.value / "functional-tests",
    FunctionalTest / resourceDirectory := baseDirectory.value / "functional-tests" / "resources",

    Test / javaOptions += "-Dconfig.resource=test.conf",
    Test / fork := true // spin up a fresh JVM for the test run (do not run inside the existing sbt JVM or javaOptions is not applied)
  )