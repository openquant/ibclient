import AssemblyKeys._

assemblySettings

org.scalastyle.sbt.ScalastylePlugin.Settings

name := "ibclient"

version := "0.2.0-SNAPSHOT"

organization := "com.larroy"

scalaVersion := "2.11.5"

scalacOptions := Seq("-unchecked", "-deprecation", "-feature", "-encoding", "utf8")

// stackoverflow.com/questions/24310889/how-to-redirect-aws-sdk-logging-output
resolvers += "version99 Empty loggers" at "http://version99.qos.ch"

libraryDependencies ++= {
  Seq(
    "org.specs2" %% "specs2" % "2.3.12" % "test",
    "com.github.seratch" %% "awscala" % "0.2.+" excludeAll(ExclusionRule(organization= "org.apache.commons.logging")),
    "com.github.scopt" %% "scopt" % "3.2.0",
    "org.slf4j" % "jcl-over-slf4j" % "1.7.7",
    "commons-logging" % "commons-logging" % "99-empty",
    "ch.qos.logback" % "logback-classic" % "1.0.13",
    "com.github.tototoshi" %% "scala-csv" % "1.1.2",
    "com.typesafe" % "config" % "1.2.1",
    "net.ceedubs" %% "ficus" % "1.1.2",
    "io.reactivex" %% "rxscala" % "0.23.1",
    "com.google.guava" % "guava" % "18.0",
    "joda-time" % "joda-time" % "2.7"
  )
}

resolvers += Resolver.sonatypeRepo("public")

testOptions in Test += Tests.Argument(TestFrameworks.Specs2, "console", "junitxml")


parallelExecution in Test := false
