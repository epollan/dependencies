package dependencies.parsing

import java.util.Comparator

import com.google.common.base.Objects
import com.google.common.collect.ComparisonChain

sealed abstract class DependencyTreeToken()

final case class Preamble() extends DependencyTreeToken
final case class End() extends DependencyTreeToken

/**
 * Using Gradle terminology here -- functionally equivalent to a Maven "scope"
 */
final case class Configuration(name: String) 
  extends DependencyTreeToken with Ordered[Configuration] {

  override def compare(other: Configuration): Int = name.compareTo(other.name)
}

object Dependency {
  def parentFor(depth: Int, previous: Option[Dependency]): Option[Dependency] = {
    val parentDepth = depth - 1
    previous match {
      case d: Some[Dependency] =>
        // the previous dependency may be _more_ nested than the given depth.  If so,
        // traverse its parent chain until we find a parent that is at an appropriate depth
        var previous: Option[Dependency] = d
        while (previous.isDefined && previous.get.depth > parentDepth) {
          previous = previous.get.neededBy
        }
        previous match {
          case d: Some[Dependency] =>
            assert(d.get.depth == parentDepth)
            d
          case _ => None
        }
      case _ => None
    }
  }
}

final case class Dependency(configuration: Configuration,
                            depth: Int,
                            group: String,
                            artifact: String,
                            version: String,
                            // naive dependency modeling that's suitable for a single streaming pass through a tree rendering
                            neededBy: Option[Dependency])
  extends DependencyTreeToken with Ordered[Dependency] {

  override def compare(other: Dependency): Int = ComparisonChain.start()
      .compare(group, other.group)
      .compare(artifact, other.artifact)
      .compare(version, other.version)
      // Careful: a dependency tree parse run can emit multiple `Dependency` instances, one per dependent artifact.
      .compare(neededBy, other.neededBy, new Comparator[Option[Dependency]] {
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

  override def hashCode: Int = Objects.hashCode(group, artifact, version)
}