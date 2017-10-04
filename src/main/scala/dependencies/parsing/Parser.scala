package dependencies.parsing

import java.util.regex.Pattern

import scala.collection.AbstractIterator

abstract class Parser extends Iterator[DependencyTreeToken] {
  protected var currentToken: Option[DependencyTreeToken] = None

  def hasNext: Boolean = {
    currentToken match {
      case Some(End()) => false
      case _ => true
    }
  }

  def next(): DependencyTreeToken = {
    if (currentToken eq Some(End())) {
      throw new IllegalStateException("parser reached end of stream")
    }
    currentToken = consumeNextToken().orElse(Some(End()))
    currentToken.get
  }

  protected def consumeNextToken(): Option[DependencyTreeToken]
}

object ParseLogging {
  var verbose = false

  def lineIterator(lines: Iterator[String]): Iterator[String] =
    new AbstractIterator[String] {
      override def hasNext: Boolean = lines.hasNext
      override def next(): String = {
        val nextLine = lines.next()
        if (verbose) {
          System.err.println(nextLine)
        }
        nextLine
      }
    }
}

trait ParseLogging {
  protected def parse[T <: DependencyTreeToken](state: Class[_ <: DependencyTreeToken], parsedTo: => Option[T]): Option[T] = {
    if (ParseLogging.verbose && parsedTo.isDefined) {
      System.err.println(s"[PARSE FROM ${state.getSimpleName}] => $parsedTo")
    }
    parsedTo
  }
}

final case class GradleParser(lines: Iterator[String]) extends Parser with ParseLogging {

  private val parsedLines = ParseLogging.lineIterator(lines)

  protected def consumeNextToken(): Option[DependencyTreeToken] = {
    currentToken match {
      case None =>
        return Some(Preamble())

      case Some(Preamble()) =>
        while (parsedLines.hasNext) {
          val line = parsedLines.next()
          val project = parse(classOf[Preamble], GradleProject.parse(line))
          if (project.isDefined) {
            return project
          }
        }

      case p @ Some(Project(_)) =>
        val project = p.asInstanceOf[Some[Project]].get
        while (parsedLines.hasNext) {
          val line = parsedLines.next()
          val configuration = parse(classOf[Project], GradleConfiguration.parse(project, line))
          if (configuration.isDefined) {
            return configuration
          }
        }

      case c @ Some(Configuration(_, _)) =>
        val config = c.asInstanceOf[Some[Configuration]].get
        while (parsedLines.hasNext) {
          val line = parsedLines.next()
          val state = parse(classOf[Configuration], GradleDependency.parse(config, None, line))
            .orElse(parse(classOf[Configuration], GradleConfiguration.parse(config.project, line)))
          if (state.isDefined) {
            return state
          } 
        }

      case d @ (Some(ArtifactDependency(_, _, _, _, _, _)) | Some(ProjectDependency(_, _, _, _))) =>
        val dep = d.asInstanceOf[Some[Dependency]]
        val parseState = dep.get.getClass
        while (parsedLines.hasNext) {
          val line = parsedLines.next()
          val state = parse(parseState, GradleDependency.parse(dep.get.configuration, dep, line))
            .orElse(parse(parseState, GradleConfiguration.parse(dep.get.configuration.project, line)))
              .orElse(parse(parseState, GradleProject.parse(line)))
          if (state.isDefined) {
            return state
          }
        }

      case _ => throw new IllegalStateException("Unexpected parse state")
    }
    None
  }

  private object GradleProject {
    // Grab the project name and discard the description, if present
    private val pattern = Pattern.compile("Project :(?<project>[^ ]+)(?: - .*)?")

    def parse(line: String): Option[Project] = {
      val matcher = pattern.matcher(line)
      if (matcher.matches())
        Some(Project(matcher.group("project")))
      else
        None
    }
  }

  private object GradleConfiguration {
    private val pattern = Pattern.compile("(?<configuration>\\S+) - .*")

    def parse(project: Project, line: String): Option[Configuration] = {
      val matcher = pattern.matcher(line)
      if (matcher.matches())
        Some(Configuration(project, matcher.group("configuration")))
      else
        None
    }
  }

  private object GradleDependency {
    // "+--- " (single indent) or "|    +--- " (double indent)
    private val indent = "([^A-Za-z]{5})"
    // "1.2.3" and "1.2.3 (*)" => 1.2.3
    private val version = "(?<version>[^:\\s]+)"
    // "1.2.3 -> 1.2.4" => 1.2.4
    private val upgradedVersion = "[^: ]+ -> (?<upgradedVersion>[\\S]+)"
    // "+--- group:artifact:version" => artifact dependency
    // "+--- project :net" => project "net" dependency
    private val pattern = Pattern.compile(s"(?<indent>($indent*))" +
        "(" +
        s"((?<group>[^:]+):(?<artifact>[^:]+):((($version)|($upgradedVersion))(?: \\(\\*\\))?))" +
        "|" +
        "(project :(?<project>.*))" +
        ")"
    )

    def parse(conf: Configuration, previousDependency: Option[Dependency], line: String): Option[Dependency] = {
      val matcher = pattern.matcher(line)
      if (matcher.matches()) {
        val depth = computeDepth(matcher.group("indent"))
        val neededBy = Dependency.parentFor(depth, previousDependency, line)

        Option(matcher.group("version")).orElse(Option(matcher.group("upgradedVersion"))) match {
          case Some(versionString) =>
            Some(ArtifactDependency(
              conf,
              depth,
              matcher.group("group"),
              matcher.group("artifact"),
              versionString,
              neededBy
            ))
          case None =>
            Some(ProjectDependency(
              conf,
              depth,
              Project(matcher.group("project")),
              neededBy
            ))
        }
      } else {
        None
      }
    }

    private val indention = Pattern.compile(indent)

    private def computeDepth(indent: String): Int = {
      var depth = 0
      val matcher = indention.matcher(indent)
      while (matcher.find()) {
        depth += 1
      }
      depth
    }
  }
}
