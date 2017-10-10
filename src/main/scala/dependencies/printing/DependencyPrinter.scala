package dependencies.printing

import java.io._
import java.nio.file.{Path, Paths}

import dependencies.ModelFactory
import dependencies.model.{MavenArtifactCoordinates, MavenDependencies, MavenDependency, ProjectCoordinates}
import dependencies.parsing.{ArtifactDependency, Dependency}

import scala.collection.mutable

abstract class DependencyPrinter(w: PrintWriter) {
  def printHeader(): Unit
  def print(d: Dependency): Unit
  def close(): Unit = w.close()

  protected def versionDetailsUrl(d: Dependency): String = d match {
    case a: ArtifactDependency =>
      s"http://search.maven.org/#artifactdetails%7C${a.group}%7C${a.artifact}%7C${a.version}%7Cjar"
    case _ => ""
  }

  protected def artifactVersionsUrl(d: Dependency): String = d match {
    case a: ArtifactDependency => s"http://search.maven.org/#search%7Cgav%7C1%7Cg%3A%22${a.group}%22%20AND%20a%3A%22${a.artifact}%22"
    case _ => ""
  }
}

/**
  * Emits content designed to be placed in the following files:
  * {{{
  *   WORKSPACE
  *   third_party.bzl
  *   third_party/BUILD
  * }}}
  *
  * Then, in your bazel module, you can do things like add the following top-level dependency
  * to relevant modules BUILD files (or BUILD.bazel files, if you're trying to prevent collisions
  * with Gradle `build` dirs):
  * {{{
  *   java_library(
  *       name = "my-lib",
  *       srcs = glob(["*.java"]),
  *       visibility = ["//visibility:public"],
  *       deps = [
  *           "//third_party:SNAKE_CASE_GROUP_PLUS_ARTIFACT_1",
  *           "//third_party:SNAKE_CASE_GROUP_PLUS_ARTIFACT_2",
  *       ],
  *   )
  * }}}
  */
final case class BazelPrinter(w: PrintWriter,
                              repoServer: String,
                              writeFiles: Boolean = false) extends DependencyPrinter(w) {
  val dependencies: mutable.MutableList[Dependency] = mutable.MutableList()
  val resources: mutable.MutableList[AutoCloseable] = mutable.MutableList()

  override def printHeader(): Unit = {}

  // Gather deps -- we need a wholistic view of _all_ dependencies before we can write out anything meaningful
  override def print(d: Dependency): Unit = dependencies.+=(d)

  override def close(): Unit = {
    val mavenDependencies = ModelFactory.forParseResult(dependencies)

    val thirdPartyDotBzlStream = printStreamFor(Paths.get(".", "third_party.bzl"))
    thirdPartyDotBzlStream.println("#######################")
    thirdPartyDotBzlStream.println("# Generated and designed to be cat'ed into <root>/third_party.bzl")
    thirdPartyDotBzlStream.println("#######################")
    thirdPartyDotBzl(mavenDependencies, thirdPartyDotBzlStream)

    printStreamFor(Paths.get(".", "WORKSPACE")).println(
      s"""#######################
         |# Copy the following content into <root>/WORKSPACE:
         |#
         |# NOTE: this presupposes a ~/.m2/settings.xml file with credentials for a
         |#       server named "$repoServer".  Your settings file may only have credentials for
         |#       a "spredfast-repository" -- this name is not legal for a bazel `maven_server`
         |#       object, so just duplicate the credentials into a server named "$repoServer".
         |#######################
         |maven_server(
         |    name = "$repoServer",
         |    url = "https://buildrepo.sf-ops.net/artifactory/sf-repo"
         |)
         |
         |load("//:third_party.bzl", "generated_maven_jars")
         |generated_maven_jars()
          """.stripMargin
    )

    printStreamFor(Paths.get("third_party", "BUILD.bazel")).println(
      """#######################
        |# Copy the following content into <root>/third_party/BUILD.bazel:
        |#######################
        |
        |package(default_visibility = ["//visibility:public"])
        |load("//:third_party.bzl", "generated_java_libraries")
        |generated_java_libraries()
      """.stripMargin
    )

    mavenDependencies.roots.toSeq
        .sortBy { case (project: ProjectCoordinates, _: Set[MavenDependency[_]]) => project.name }
        .foreach { case (project: ProjectCoordinates, deps: Set[MavenDependency[_]]) =>
          val projectStream = printStreamForProject(project)
          projectStream.println(
            s"""#######################
               |# Copy the following snippet into any applicable java_binary/java_library
               |# `deps` sections in the BUILD/BUILD.bazel file for project: ${project.name}
               |#######################
               |
               |package(default_visibility = ["//visibility:public"])
               |java_library(
               |    name = "${project.name}",
               |    srcs = glob(["src/main/java/**/*.java"]),
               |    deps = [""".stripMargin
          )
          deps.map(_.coordinates.packageQualifiedBazelName).toSeq.sorted.foreach(root => {
            projectStream.println(s"""        "$root",""")
          })
          projectStream.println(
            """    ]
              |)
              |""".stripMargin
          )
        }

    resources.foreach(_.close)
    super.close()
  }

  private def printStreamForProject(project: ProjectCoordinates): PrintStream = {
    if (!writeFiles) {
      return System.err
    }
    printStreamFor(project.relativeProjectPath.resolve("BUILD.bazel"))
  }

  private def printStreamFor(path: Path): PrintStream = {
    if (!writeFiles) {
      return System.err
    }

    val target = path.toAbsolutePath.toFile
    val dir = target.getParentFile
    if (dir == null || !dir.exists()) {
      throw new IllegalStateException(s"Could not write a file to $target since the directory $dir does not exist")
    }

    val stream = new PrintStream(new FileOutputStream(target))
    resources.+=(stream)
    stream
  }

  private def thirdPartyDotBzl(mavenDependencies: MavenDependencies, printStream: PrintStream) = {
    val thirdPartyDeps = mavenDependencies.thirdParty.toSeq.sorted

    printStream.println(
      s"""
         |# `generated_maven_jars()` is designed to be executed this within your module's WORKSPACE file, like:
         |#
         |#     load("//:third_party.bzl", "generated_maven_jars")
         |#     generated_maven_jars()
         |
         |def generated_maven_jars():
         |""".stripMargin
    )
    thirdPartyDeps.foreach(mavenDependency => {

      printStream.println(
        s"""  native.maven_jar(
           |      name = "${mavenDependency.coordinates.bazelName}",
           |      artifact = "${mavenDependency.coordinates}",
           |      server = "$repoServer"
           |  )
           |""".stripMargin
      )
    })

    printStream.println(
      s"""
         |# `generated_java_libraries()` is designed to be executed within `third_party/BUILD`
         |#
         |#     load("//:third_party.bzl", "generated_java_libraries")
         |#     generated_java_libraries()
         |
         |def generated_java_libraries():
         |""".stripMargin
    )

    def nativeJavaLibrary(mavenDependency: MavenDependency[MavenArtifactCoordinates]): Unit = {
      val name = mavenDependency.coordinates.bazelName
      printStream.println(
        s"""  native.java_library(
           |      name = "$name",
           |      visibility = ["//visibility:public"],
           |      exports = ["@$name//jar"],
           |      runtime_deps = [""".stripMargin
      )

      def dependencyNames(mavenDependency: MavenDependency[MavenArtifactCoordinates]): Set[String] =
        Set(mavenDependency.coordinates.bazelName) ++ mavenDependency.dependsOn.flatMap(dependencyNames)

      mavenDependency.dependsOn
          .flatMap(dependencyNames)
          .toSeq
          .sorted
          .foreach(dependencyName => printStream.println(s"""          ":$dependencyName","""))

      printStream.println(
        s"""      ]
           |  )
           |""".stripMargin
      )
    }

    // Spit out "leaves" first
    thirdPartyDeps
        .filter(_.dependsOn.isEmpty)
        .foreach(nativeJavaLibrary)

    thirdPartyDeps
        .filter(_.dependsOn.nonEmpty)
        .foreach(nativeJavaLibrary)
  }
}

final case class RawPrinter(w: PrintWriter) extends DependencyPrinter(w) {
  override def printHeader(): Unit = {}
  override def print(d: Dependency): Unit = w.println(d)
}

abstract class CsvPrinter(w: PrintWriter) extends DependencyPrinter(w) {
  override def printHeader(): Unit = w.println("Dependency,Version,License,Notes")

  override def print(d: Dependency): Unit = d match {
    case a: ArtifactDependency =>
      w.append('"')
          .append(escapeQuotes(hyperlink(artifactVersionsUrl(d), a.group + ":" + a.artifact)))
          .append("\", \"")
          .append(escapeQuotes(hyperlink(versionDetailsUrl(d), a.version)))
          .append("\", ,")
          .println()
    case _ =>
  }

  def hyperlink(href: String, text: String): String

  private def escapeQuotes(s: String): String = s.replace("\"", "\"\"")
}

/**
 * Assuming Google docs hyperlink formatting
 */
final case class GoogleDocsCsvPrinter(w: PrintWriter) extends CsvPrinter(w) {
  override def hyperlink(href: String, text: String): String = s"""=HYPERLINK("$href", "$text")"""
}

final case class ConfluencePrinter(w: PrintWriter) extends DependencyPrinter(w) {
  override def printHeader(): Unit = w.println("||Dependency||Version||License||Notes||")

  override def print(d: Dependency): Unit = d match {
    case a: ArtifactDependency =>
      w.append("| [")
          .append(a.group)
          .append(":")
          .append(a.artifact)
          .append("|")
          .append(artifactVersionsUrl(d))
          .append("] | [")
          .append(a.version)
          .append("|")
          .append(versionDetailsUrl(d))
          .append("] | | |")
          .println()
    case _ =>
  }
}
