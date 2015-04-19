name := "orient_loadtest"

version := "1.0"

scalaVersion := "2.10.4"

resolvers += Resolver.sonatypeRepo("snapshots")

libraryDependencies ++= Seq(
  "com.typesafe" %% "scalalogging-slf4j" % "1.1.0" ,
  "ch.qos.logback" % "logback-classic" % "1.0.13",
  "org.no-hope" % "test-utils-stress" % "0.1.8"
).map(_.exclude("commons-beanutils", "commons-beanutils")
  .exclude("commons-collections", "commons-collections")
  )

val orientDbVersion = "2.1-SNAPSHOT"

libraryDependencies ++= Seq(
  // documentdb
  "com.orientechnologies" % "orientdb-core" % orientDbVersion
  , "com.orientechnologies" % "orientdb-client" % orientDbVersion
  , "com.orientechnologies" % "orientdb-enterprise" % orientDbVersion
  // graphdb
  , "com.orientechnologies" % "orientdb-graphdb" % orientDbVersion
  , "com.tinkerpop.blueprints" % "blueprints-core" % "2.6.0"
).map(_.exclude("commons-beanutils", "commons-beanutils")
  .exclude("commons-collections", "commons-collections")
  )



assemblyMergeStrategy in assembly := {
  case PathList("builddef.lst") => MergeStrategy.discard
  case x =>
    val oldStrategy = (assemblyMergeStrategy in assembly).value
    oldStrategy(x)
}