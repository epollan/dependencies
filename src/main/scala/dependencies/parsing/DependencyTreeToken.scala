package dependencies.parsing

import java.util.Comparator

import com.google.common.collect.ComparisonChain
import dependencies.model.{ArtifactCoordinates, MavenArtifactCoordinates, ProjectCoordinates}

sealed abstract class DependencyTreeToken()

final case class Preamble() extends DependencyTreeToken
final case class End() extends DependencyTreeToken

final case class Project(name: String)
extends DependencyTreeToken with Ordered[Project] {
  override def compare(that: Project): Int = name.compareTo(that.name)
}

/**
 * Using Gradle terminology here -- functionally equivalent to a Maven "scope"
 */
final case class Configuration(project: Project, name: String)
    extends DependencyTreeToken with Ordered[Configuration] {
  override def compare(other: Configuration): Int = name.compareTo(other.name)
}

object Dependency {
  def parentFor(depth: Int, previous: Option[Dependency], line: String): Option[Dependency] = {
    val parentDepth = depth - 1
    previous match {
      case d: Some[Dependency] =>
        // the previous dependency may be _more_ nested than the given depth.  If so,
        // traverse its parent chain until we find a parent that is at an appropriate depth
        var parentCandidate: Option[Dependency] = d
        while (parentCandidate.isDefined && parentCandidate.get.depth > parentDepth) {
          parentCandidate = parentCandidate.get.neededBy
        }
        if (parentCandidate.isDefined && parentCandidate.get.depth != parentDepth) {
          throw new AssertionError(s"Looking for a parent with depth $parentDepth, but ended up with depth ${parentCandidate.get.depth}\nprevious:\n$previous\n\nparentCandidate:\n$parentCandidate\n\nline:\n$line")
        }
        parentCandidate
      case _ => None
    }
  }
}

abstract class Dependency(val configuration: Configuration,
                          val depth: Int,
                          val neededBy: Option[Dependency],
                          val coordinates: ArtifactCoordinates)
    extends DependencyTreeToken with Ordered[Dependency] {
  
  val name: String = coordinates.toString

  override def compare(that: Dependency): Int = ComparisonChain.start()
      .compare(name, that.name)
      .compare(neededBy, that.neededBy, new Comparator[Option[Dependency]] {
        override def compare(left: Option[Dependency], right: Option[Dependency]): Int = (left, right) match {
          case (None, None) => 0
          case (Some(_), None) => -1
          case (None, Some(_)) => 1
          case (Some(l), Some(r)) => l.compare(r)
        }
      })
      .result()

  // Squash identity down to those attributes used for sorting
  override def equals(o: Any): Boolean = {
    o match {
      case d: Dependency => compare(d) == 0
      case _ => false
    }
  }

  override def hashCode: Int = coordinates.hashCode()
}

final case class ProjectDependency(override val configuration: Configuration,
                                   override val depth: Int,
                                   project: Project,
                                   override val neededBy: Option[Dependency])
    extends Dependency(configuration, depth, neededBy, ProjectCoordinates(project.name))


final case class ArtifactDependency(override val configuration: Configuration,
                                    override val depth: Int,
                                    group: String,
                                    artifact: String,
                                    version: String,
                                    // naive dependency modeling that's suitable for a single streaming pass through a tree rendering
                                    override val neededBy: Option[Dependency])
    extends Dependency(configuration, depth, neededBy, MavenArtifactCoordinates(group, artifact, version))
