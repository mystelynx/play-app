import play.PlayImport.PlayKeys._

name := "play-app"

version := "1.0"

lazy val `play-app` = (project in file("."))
  .enablePlugins(PlayScala)
  .configs(IntegrationTest)
  .settings(Defaults.itSettings: _*)

scalaVersion := "2.11.1"

val libraries = Seq (
  jdbc,
  cache,
  ws,
  "org.scalikejdbc" %% "scalikejdbc" % "2.2.+",
  "org.scalikejdbc" %% "scalikejdbc-play-plugin" % "2.3.+",
  "org.scalikejdbc" %% "scalikejdbc-config" % "2.2.+",
  "org.scalikejdbc" %% "scalikejdbc-test" % "2.2.+" % "test",
  "com.github.tototoshi" %% "play-flyway" % "1.2.1",
  "org.mindrot" % "jbcrypt" % "0.3m",
  "org.postgresql" % "postgresql" % "9.2-1004-jdbc41" % "runtime",
  "ws.securesocial" %% "securesocial" % "3.0-M3",
  "org.scaldi" %% "scaldi-play" % "0.5.4",
  "com.typesafe.play" %% "play-mailer" % "2.4.0",
  "com.icegreen" % "greenmail" % "1.4.0" % "test"
)

val itLibraries = Seq(
  "org.specs2" %% "specs2" % "2.3.12" % "it",
  "org.scalikejdbc" %% "scalikejdbc-test" % "2.2.+" % "it",
  "com.typesafe.play" %% "play-test" % play.core.PlayVersion.current % "it"
)

resolvers += Resolver.sonatypeRepo("snapshots")

libraryDependencies ++= libraries ++ itLibraries

unmanagedResourceDirectories in Test <+=  baseDirectory ( _ /"target/web/public/test" )

javaOptions in Test += "-Dconfig.resource=application.ut.conf"

logBuffered in Test := false

fork in IntegrationTest := true

javaOptions in IntegrationTest += "-Dconfig.resource=application.it.conf"

javaSource in IntegrationTest := baseDirectory( _ / "test-integration").value

routesImport ++= Seq("controllers.Implicits._")
