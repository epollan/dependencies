package dependencies.printing

import java.io.PrintWriter

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
final case class BazelPrinter(w: PrintWriter) extends DependencyPrinter(w) {
  val dependencies: mutable.MutableList[Dependency] = mutable.MutableList()

  override def printHeader(): Unit = {}

  // Gather deps -- we need a wholistic view of _all_ dependencies before we can write out anything meaningful
  override def print(d: Dependency): Unit = dependencies.+=(d)

  override def close(): Unit = {
    val mavenDependencies = ModelFactory.forParseResult(dependencies)

    w.println("#######################")
    w.println("# Generated and designed to be cat'ed into <root>/third_party.bzl")
    w.println("#######################")
    thirdPartyDotBzl(mavenDependencies)

    System.err.println(
      """#######################
        |# Copy the following content into <root>/WORKSPACE:
        |#######################
        |
        |load("//:third_party.bzl", "generated_maven_jars")
        |generated_maven_jars()
      """.stripMargin
    )

    System.err.println(
      """#######################
        |# Copy the following content into <root>/third_party/BUILD:
        |#######################
        |
        |package(default_visibility = ["//visibility:public"])
        |load("//:third_party.bzl", "generated_java_libraries")
        |generated_java_libraries()
      """.stripMargin
    )

    mavenDependencies.roots.toSeq
        .sortBy { case (project: ProjectCoordinates, _: Set[MavenDependency]) => project.name }
        .foreach { case (project: ProjectCoordinates, deps: Set[MavenDependency]) =>
          System.err.println(
            s"""#######################
               |# Copy the following snippet into any applicable java_binary/java_library
               |# `deps` sections in the BUILD/BUILD.bazel file for project: ${project.name}
               |#######################
               |
               |java_library(
               |    name = "${project.name}",
               |    srcs = glob(["src/main/java/**/*.java"]),
               |    deps = [""".stripMargin
          )
          deps.map(_.coordinates.packageQualifiedBazelName).toSeq.sorted.foreach(root => {
            System.err.println(s"""        "$root",""")
          })
          System.err.println(
            """    ]
              |)
              |""".stripMargin
          )
        }

    super.close()
  }

  private def thirdPartyDotBzl(mavenDependencies: MavenDependencies) = {
    val thirdPartyDeps = mavenDependencies.thirdParty.toSeq.sorted

    w.println(
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
      w.println(
        s"""  native.maven_jar(
           |      name = "${mavenDependency.coordinates.bazelName}",
           |      artifact = "${mavenDependency.coordinates}",
           |  )
           |""".stripMargin
      )
    })

    w.println(
      s"""
         |# `generated_java_libraries()` is designed to be executed within `third_party/BUILD`
         |#
         |#     load("//:third_party.bzl", "generated_java_libraries")
         |#     generated_java_libraries()
         |
         |def generated_java_libraries():
         |""".stripMargin
    )

    def nativeJavaLibrary(mavenDependency: MavenDependency): Unit = {
      val name = mavenDependency.coordinates.bazelName
      w.println(
        s"""  native.java_library(
           |      name = "$name",
           |      visibility = ["//visibility:public"],
           |      exports = ["@$name//jar"],
           |      runtime_deps = [""".stripMargin
      )

      def dependencyNames(mavenDependency: MavenDependency): Set[String] =
        Set(mavenDependency.coordinates.bazelName) ++ mavenDependency.dependsOn.flatMap(dependencyNames)

      mavenDependency.dependsOn
          .flatMap(dependencyNames)
          .toSeq
          .sorted
          .foreach(dependencyName => w.println(s"""          ":$dependencyName","""))

      w.println(
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
