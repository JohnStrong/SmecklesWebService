ThisBuild / scalaVersion := "3.3.7"   // use a recent stable Scala 3 version
ThisBuild / version := "0.1.0"
ThisBuild / organization := "org.myapps.shoppinglistservice"

lazy val root = (project in file("."))
  .enablePlugins(PlayScala)
  .settings(
    name := "SimpleShoppingListApp",
    libraryDependencies ++= Seq(
      guice,
      "org.scalatest" %% "scalatest" % "3.2.20" % Test,
      "org.scalatestplus.play" %% "scalatestplus-play" % "7.0.1" % Test,
      "org.mockito" % "mockito-core" % "5.22.0" % Test
    )
  )