name := "context-switch"

version := "0.1"

scalaVersion := "2.12.8"

lazy val AkkaVersion = "2.5.21"

libraryDependencies += "org.scalatest" %% "scalatest" % "3.0.5" % Test
libraryDependencies += "com.typesafe.akka" %% "akka-actor" % AkkaVersion
libraryDependencies += "com.typesafe.akka" %% "akka-stream" % AkkaVersion
scalacOptions += "-deprecation"
