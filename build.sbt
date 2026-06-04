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
    libraryDependencies ++= Seq(
      guice,
      "org.scalatest" %% "scalatest" % "3.2.20" % Test,
      "org.scalatestplus.play" %% "scalatestplus-play" % "7.0.1" % Test,
      "org.mockito" % "mockito-core" % "5.22.0" % Test,
      "org.playframework" %% "play-slick"            % "6.1.1",     // object-relational mapping
      "org.playframework" %% "play-slick-evolutions" % "6.1.1",     // Slick + Evolutions
      "com.h2database" % "h2" % "2.2.224",                          // H2 driver (dev db)
      "org.postgresql"     % "postgresql"            % "42.7.3",    // production driver (postgres)
      evolutions                                                    // Play Evolutions for schema management
    ),
    // Functional test configuration - run with sbt functional:test
    inConfig(FunctionalTest)(Defaults.testSettings),

    FunctionalTest / sourceDirectory := baseDirectory.value / "functional-tests",
    FunctionalTest / scalaSource := baseDirectory.value / "functional-tests",
    FunctionalTest / resourceDirectory := baseDirectory.value / "functional-tests" / "resources",

    Test / javaOptions += "-Dconfig.resource=test.conf",
    Test / fork := true // spin up a fresh JVM for the test run (do not run inside the existing sbt JVM or javaOptions is not applied)
  )