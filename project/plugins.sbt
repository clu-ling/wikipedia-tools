addSbtPlugin("se.marcuslonnberg" % "sbt-docker" % "1.4.1")
addSbtPlugin("com.eed3si9n" % "sbt-assembly" % "0.14.5")

// dependency required to silence warning caused by sbt-git:
//   SLF4J: Failed to load class "org.slf4j.impl.StaticLoggerBinder".
libraryDependencies += "org.slf4j" % "slf4j-nop" % "1.7.21"
addSbtPlugin("com.typesafe.sbt" % "sbt-git" % "0.9.3")

logLevel := Level.Warn