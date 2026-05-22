ThisBuild / scalaVersion := "3.2.2"    // use a recent stable Scala 3 version
ThisBuild / version := "0.1.0"
ThisBuild / organization := "org.myapps.shoppinglistservice"

lazy val root = (project in file("."))
  .settings(
    name := "SimpleShoppingListApp",
    libraryDependencies ++= Seq(
      "org.springframework.boot" % "spring-boot-starter-data-jpa" % "3.3.0",
      "org.springframework.boot" % "spring-boot-starter-web" % "3.3.0",
      "org.scalatest" %% "scalatest" % "3.2.16" % Test
    )
  )