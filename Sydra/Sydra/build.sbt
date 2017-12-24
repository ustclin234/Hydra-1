name := "sydra"
version := "0.6.0"
scalaVersion := "2.12.4"
organization := "com.hydra"
libraryDependencies += "org.scala-lang" % "scala-reflect" % "2.12.4"
libraryDependencies += "org.scala-lang" % "scala-compiler" % "2.12.4"
libraryDependencies += "org.msgpack" % "msgpack-core" % "0.8.13"
libraryDependencies += "org.msgpack" % "jackson-dataformat-msgpack" % "0.8.13"
libraryDependencies += "org.apache.logging.log4j" % "log4j" % "2.4"
libraryDependencies += "org.apache.logging.log4j" % "log4j-slf4j-impl" % "2.4"
libraryDependencies += "org.apache.logging.log4j" % "log4j-api" % "2.4"
libraryDependencies += "org.apache.logging.log4j" % "log4j-core" % "2.4"
libraryDependencies += "io.netty" % "netty-all" % "4.1.4.Final"
libraryDependencies += "io.spray" %%  "spray-json" % "1.3.3"
libraryDependencies += "org.pegdown" % "pegdown" % "1.6.0"
libraryDependencies += "org.scalatest" %% "scalatest" % "3.0.1" % "test"
scalacOptions ++= Seq("-feature")
scalacOptions ++= Seq("-deprecation")