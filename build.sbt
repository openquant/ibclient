org.scalastyle.sbt.ScalastylePlugin.Settings

name := "ibclient"

version := "0.2.2-SNAPSHOT"

organization := "com.larroy"

scalaVersion := "2.11.8"

scalacOptions := Seq("-unchecked", "-deprecation", "-feature", "-encoding", "utf8")

libraryDependencies ++= {
  Seq(
    "org.specs2" %% "specs2" % "3.+" % "test",
    "com.github.scopt" %% "scopt" % "3.2.0",
    "ch.qos.logback" % "logback-classic" % "1.1.+",
    "org.slf4j" % "jcl-over-slf4j" % "1.7.+",
    "com.github.tototoshi" %% "scala-csv" % "1.+",
    "com.typesafe" % "config" % "1.3.+",
    "com.iheart" %% "ficus" % "1.2.3",
    "io.reactivex" %% "rxscala" % "0.26.+",
    "com.google.guava" % "guava" % "18.0",
    "joda-time" % "joda-time" % "2.9.1",
    "org.joda" % "joda-convert" % "1.2",
    "org.scala-lang" % "scala-reflect" % "2.11.8"
  )
}

resolvers += Resolver.sonatypeRepo("public")

testOptions in Test += Tests.Argument(TestFrameworks.Specs2, "console", "junitxml")


parallelExecution in Test := false

test in assembly := {}
