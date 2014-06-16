package dependencies.parsing

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

	override def compare(other: Configuration) = name.compareTo(other.name)
}

final case class Dependency(configuration: Configuration,
							depth: Int,
							group: String,
							artifact: String,
							version: String)
	extends DependencyTreeToken with Ordered[Dependency] {

	override def compare(other: Dependency): Int = ComparisonChain.start()
		.compare(group, other.group)
		.compare(artifact, other.artifact)
		.compare(version, other.version)
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