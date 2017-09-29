name := "dependencies"

version := "1.0"

scalaVersion := "2.11.6"

Seq(com.github.retronym.SbtOneJar.oneJarSettings: _*)

libraryDependencies ++= Seq(
	"commons-lang" % "commons-lang" % "2.6",
	"com.google.guava" % "guava" % "23.0"
)

mainClass in oneJar := Some("dependencies.CLI")
