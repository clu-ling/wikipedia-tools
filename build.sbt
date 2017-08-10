
// use commit hash as the version
enablePlugins(GitVersioning)

git.uncommittedSignifier := Some("DIRTY") // with uncommitted changes?

lazy val commonScalacOptions = Seq(
  "-feature",
  "-unchecked",
  "-deprecation",
  "-Xlint",
  "-Yno-adapted-args",
  "-Ywarn-dead-code",
  // "-Ywarn-value-discard",
  "-Ywarn-unused",
  "-encoding", "utf8"
)

lazy val commonSettings = Seq(
  organization := "org.clulab",
  scalaVersion in ThisBuild := "2.11.11", // avoid warnings when compiling play project with -Ywarn-unused
  // we want to use -Ywarn-unused-import most of the time
  scalacOptions ++= commonScalacOptions,
  scalacOptions += "-Ywarn-unused-import",
  // -Ywarn-unused-import is annoying in the console
  scalacOptions in (Compile, console) := commonScalacOptions,
  // show test duration
  testOptions in Test += Tests.Argument("-oD"),
  excludeDependencies += "commons-logging" % "commons-logging"
)


lazy val assemblySettings = Seq(
  mainClass in assembly := Some("org.clulab.wikipedia.Main"),
  assemblyMergeStrategy in assembly := {
    case PathList("javax", "servlet", xs @ _*) => MergeStrategy.first
    case refOverrides if refOverrides.endsWith("reference-overrides.conf") => MergeStrategy.first
    case logback if logback.endsWith("logback.xml") => MergeStrategy.first
    case netty if netty.endsWith("io.netty.versions.properties") => MergeStrategy.first
    case "messages" => MergeStrategy.concat
    case PathList("META-INF", "terracotta", "public-api-types") => MergeStrategy.concat
    case PathList("play", "api", "libs", "ws", xs @ _*) => MergeStrategy.first
    // case server if server.endsWith("reference-overrides.conf")
    case x =>
      val oldStrategy = (assemblyMergeStrategy in assembly).value
      oldStrategy(x)
  }
)

lazy val root = (project in file("."))
  .settings(commonSettings)
  .settings(assemblySettings)
  .settings(
    name := "wikipedia-to-text",
    aggregate in test := false
    //aggregate in assembly := false,
    //test in assembly := {},
    // fat jar
  )

libraryDependencies ++= {

  val procVersion = "6.0.7"

  Seq(
    "com.lihaoyi" %% "fastparse" % "0.4.3",
    "org.clulab" %% "processors-main" % procVersion,
    "org.clulab" %% "processors-odin" % procVersion,
    "ai.lum" %% "common" % "0.0.7",
    "com.github.scopt" %% "scopt" % "3.6.0",
    "org.wikiclean" % "wikiclean" % "1.0",
    "com.typesafe.scala-logging" %%  "scala-logging" % "3.5.0"
  )

}

excludeDependencies += "com.github.scopt" % "scopt_2.10"



    