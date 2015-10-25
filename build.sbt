org.scalastyle.sbt.ScalastylePlugin.Settings

name := "ibclient"

version := "0.2.0-SNAPSHOT"

organization := "com.larroy"

scalaVersion := "2.11.5"

scalacOptions := Seq("-unchecked", "-deprecation", "-feature", "-encoding", "utf8")

libraryDependencies ++= {
  Seq(
    "org.specs2" %% "specs2" % "2.3.12" % "test",
    "com.github.scopt" %% "scopt" % "3.2.0",
    "ch.qos.logback" % "logback-classic" % "1.0.13",
    "org.slf4j" % "jcl-over-slf4j" % "1.7.7",
    "com.github.tototoshi" %% "scala-csv" % "1.1.2",
    "com.typesafe" % "config" % "1.2.1",
    "net.ceedubs" %% "ficus" % "1.1.2",
    "io.reactivex" %% "rxscala" % "0.23.1",
    "com.google.guava" % "guava" % "18.0",
    "joda-time" % "joda-time" % "2.9",
    "org.joda" % "joda-convert" % "1.2"
  )
}

resolvers += Resolver.sonatypeRepo("public")

testOptions in Test += Tests.Argument(TestFrameworks.Specs2, "console", "junitxml")


parallelExecution in Test := false
