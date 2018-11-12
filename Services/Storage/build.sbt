name := "sydra-storage"
version := "0.3.0"
scalaVersion := "2.12.7"
organization := "com.hydra"
libraryDependencies += "org.scala-lang" % "scala-reflect" % "2.12.7"
libraryDependencies += "org.scala-lang" % "scala-compiler" % "2.12.7"
//libraryDependencies += "org.msgpack" % "msgpack-core" % "0.8.16"
//libraryDependencies += "org.msgpack" % "jackson-dataformat-msgpack" % "0.8.16"
libraryDependencies += "org.apache.logging.log4j" % "log4j" % "2.11.1"
libraryDependencies += "org.apache.logging.log4j" % "log4j-slf4j-impl" % "2.11.1"
libraryDependencies += "org.apache.logging.log4j" % "log4j-api" % "2.11.1"
libraryDependencies += "org.apache.logging.log4j" % "log4j-core" % "2.11.1"
libraryDependencies += "io.netty" % "netty-all" % "4.1.31.Final"
libraryDependencies += "gov.nasa.gsfc.heasarc" % "nom-tam-fits" % "1.15.2"
//libraryDependencies += "org.pegdown" % "pegdown" % "1.6.0"
libraryDependencies += "com.hydra" %% "sydra" % "0.6.0"
libraryDependencies += "org.scalatest" %% "scalatest" % "3.0.5" % "test"
scalacOptions ++= Seq("-feature")
scalacOptions ++= Seq("-deprecation")