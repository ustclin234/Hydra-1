enablePlugins(JavaServerAppPackaging, UniversalDeployPlugin, WindowsPlugin, WindowsDeployPlugin)

name := """play-scala-seed"""
organization := "com.example"

version := "1.0-SNAPSHOT"

lazy val root = (project in file(".")).enablePlugins(PlayScala)

scalaVersion := "2.12.8"

crossScalaVersions := Seq("2.12.8", "2.11.12")

libraryDependencies += "com.hydra" %% "sydra-core" % "0.1.0"
libraryDependencies += guice
libraryDependencies += "org.scalatestplus.play" %% "scalatestplus-play" % "4.0.1" % Test
