name := "wydra"
version := "0.2.0"
scalaVersion := "2.12.7"
organization := "com.hydra"
libraryDependencies += "com.hydra" %% "sydra" % "0.6.0"
libraryDependencies += "org.eclipse.jetty" % "jetty-server" % "9.4.12.v20180830"
libraryDependencies += "org.eclipse.jetty" % "jetty-webapp" % "9.4.12.v20180830"
libraryDependencies += "org.eclipse.jetty" % "jetty-servlet" % "9.4.12.v20180830"
libraryDependencies += "org.pegdown" % "pegdown" % "1.6.0"
libraryDependencies += "com.hwaipy" %% "hydrogen" % "0.3.0"
libraryDependencies += "org.scalatest" %% "scalatest" % "3.0.5" % "test"
scalacOptions ++= Seq("-feature")
scalacOptions ++= Seq("-deprecation")