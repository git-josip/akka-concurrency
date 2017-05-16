import com.typesafe.sbt.SbtMultiJvm
import com.typesafe.sbt.SbtMultiJvm.MultiJvmKeys.{MultiJvm}

name := "AkkaInvestigation"
version := "0.1"
scalaVersion := "2.12.1"
resolvers += "Typesafe Repository" at "http://repo.typesafe.com/typesafe/releases/"
libraryDependencies ++= Seq(
  "com.typesafe.akka" % "akka-actor_2.12" % "2.5.1",
  "org.scalatest" % "scalatest_2.12" % "3.0.1",
  "com.typesafe.akka" % "akka-testkit_2.12" % "2.5.1",
  "com.typesafe.akka" % "akka-remote_2.12" % "2.5.1"
)
