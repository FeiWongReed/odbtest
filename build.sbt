name := "orient_loadtest"

version := "1.0"

scalaVersion := "2.11.3"

resolvers += Resolver.sonatypeRepo("snapshots")

resolvers += Resolver.sonatypeRepo("releases")

def excludeBad(seq: Seq[ModuleID]) = {
  seq.map(_.exclude("commons-beanutils", "commons-beanutils")
    .exclude("commons-collections", "commons-collections")
  )
}

libraryDependencies ++= excludeBad(Seq(
  "com.typesafe.scala-logging" %% "scala-logging" % "3.1.0",
  "ch.qos.logback" % "logback-classic" % "1.0.13",
  "org.no-hope" % "test-utils-stress" % "0.2.2"
))

val orientDbVersion = "2.1-SNAPSHOT"

libraryDependencies ++= excludeBad(Seq(
  // documentdb
  "com.orientechnologies" % "orientdb-core" % orientDbVersion
  , "com.orientechnologies" % "orientdb-client" % orientDbVersion
  , "com.orientechnologies" % "orientdb-enterprise" % orientDbVersion
  // graphdb
  , "com.orientechnologies" % "orientdb-graphdb" % orientDbVersion
  , "com.tinkerpop.blueprints" % "blueprints-core" % "2.6.0"
))

assemblyMergeStrategy in assembly := {
  case PathList("builddef.lst") => MergeStrategy.discard

  case x =>
    val oldStrategy = (assemblyMergeStrategy in assembly).value
    oldStrategy(x)
}
