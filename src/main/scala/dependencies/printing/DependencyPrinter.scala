package dependencies.printing

import java.io.PrintWriter

import dependencies.{MavenDependencies, MavenDependency}
import dependencies.parsing.Dependency

import scala.collection.mutable

abstract class DependencyPrinter(w: PrintWriter) {
  def printHeader(): Unit
  def print(d: Dependency): Unit
  def close(): Unit = w.close()

  protected def versionDetailsUrl(d: Dependency): String =
    s"http://search.maven.org/#artifactdetails%7C${d.group}%7C${d.artifact}%7C${d.version}%7Cjar"

  protected def artifactVersionsUrl(d: Dependency): String =
    s"http://search.maven.org/#search%7Cgav%7C1%7Cg%3A%22${d.group}%22%20AND%20a%3A%22${d.artifact}%22"
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
    val mavenDependencies = new MavenDependencies(dependencies)

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

    System.err.println(
      """#######################
        |# Copy the following snippet into your java_binary/java_library `deps` section in its BUILD file:
        |#######################
        |
        |java_library(
        |    ...,
        |    deps: [""".stripMargin
    )
    mavenDependencies.roots.map(bazelName).foreach(root => {
      System.err.println(s"""        "//third_party:$root",""")
    })
    System.err.println(
      """    ]
        |)
        |""".stripMargin
    )
    super.close()
  }

  private def thirdPartyDotBzl(mavenDependencies: MavenDependencies) = {
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
    mavenDependencies.all.foreach(mavenDependency => {
      val name = bazelName(mavenDependency)
      w.println(
        s"""  native.maven_jar(
           |      name = "$name",
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
    mavenDependencies.all.foreach(root => {
      val name = bazelName(root)
      w.println(
        s"""  native.java_library(
           |      name = "$name",
           |      visibility = ["//visibility:public"],
           |      exports = ["@$name//jar"],
           |      runtime_deps = [""".stripMargin
      )

      def dependencyNames(mavenDependency: MavenDependency): Set[String] =
        Set(bazelName(mavenDependency)) ++ mavenDependency.dependsOn.flatMap(dependencyNames)

      root.dependsOn
          .flatMap(dependencyNames)
          .foreach(dependencyName => w.println(s"""          ":$dependencyName","""))

      w.println(
        s"""      ]
           |  )
           |""".stripMargin
      )
    })
  }
  

  private def bazelName(mavenDependency: MavenDependency): String = (
      mavenDependency.coordinates.group.split("[-\\.]").toList ++
          mavenDependency.coordinates.artifact.split("[-\\.]").toList
      ).mkString("_")
}

final case class RawPrinter(w: PrintWriter) extends DependencyPrinter(w) {
  override def printHeader() = {}
  override def print(d: Dependency) = w.println(d)
}

abstract class CsvPrinter(w: PrintWriter) extends DependencyPrinter(w) {
  override def printHeader() = w.println("Dependency,Version,License,Notes")

  override def print(d: Dependency) = {
    w.append('"')
      .append(escapeQuotes(hyperlink(artifactVersionsUrl(d), d.group + ":" + d.artifact)))
      .append("\", \"")
      .append(escapeQuotes(hyperlink(versionDetailsUrl(d), d.version)))
      .append("\", ,")
      .println()
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
  override def printHeader() = w.println("||Dependency||Version||License||Notes||")

  override def print(d: Dependency) = {
    w.append("| [")
      .append(d.group)
      .append(":")
      .append(d.artifact)
      .append("|")
      .append(artifactVersionsUrl(d))
      .append("] | [")
      .append(d.version)
      .append("|")
      .append(versionDetailsUrl(d))
      .append("] | | |")
      .println()
  }
}