name := "dependencies"

version := "1.0"

scalaVersion := "2.10.2"

seq(com.github.retronym.SbtOneJar.oneJarSettings: _*)

libraryDependencies ++= Seq(
	"commons-lang" % "commons-lang" % "2.6",
	"com.google.guava" % "guava" % "16.0.1"
)

mainClass in oneJar := Some("dependencies.CLI")
