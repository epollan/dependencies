package dependencies

import dependencies.parsing.Dependency

/**
  * Given a series of parsed dependencies from a build tool, construct the proper transitive
  * dependency graph of a module.
  *
  * NOTE: I'm pretty sure both the graph construction and flattening/deduping algorithms used
  * here would stack overflow if they encountered a cyclic graph.  I'm not sure you can get
  * into that state with, e.g., Gradle output, though...
  */
class MavenDependencies(dependencies: Seq[Dependency]) {

  private val dependentToDependencies: Map[ArtifactCoordinates, Set[ArtifactCoordinates]] =
    dependencies
        .filter(_.neededBy.isDefined)
        .groupBy(d => ArtifactCoordinates.apply(d.neededBy.get))
        .mapValues(_.map(ArtifactCoordinates.apply).toSet)

  /**
    * Root -- or "direct" -- dependencies of a module
    */
  val roots: Set[MavenDependency] = dependencies
      .filter(_.neededBy.isEmpty)
      .map(ArtifactCoordinates.apply).map(mavenDependency(_, dependentToDependencies)).toSet

  /**
    * Set of all unique dependencies in the entire transitive dependency graph for a module
    */
  val all: Set[MavenDependency] = {
    def flattenDependences(root: MavenDependency): Seq[MavenDependency] =
      Seq(root) ++ root.dependsOn.toSeq.flatMap(flattenDependences)
    roots.flatMap(flattenDependences)
  }


  private def mavenDependency(coordinates: ArtifactCoordinates,
                              dependentToDependencies: Map[ArtifactCoordinates, Set[ArtifactCoordinates]]): MavenDependency = {
    MavenDependency(
      coordinates,
      dependentToDependencies.getOrElse(coordinates, Set())
          // recurse and create MavenDependencies for this dependency's first-degree transitive dependencies
          .map(mavenDependency(_, dependentToDependencies))
    )
  }
}

case class MavenDependency(coordinates: ArtifactCoordinates,
                           dependsOn: Set[MavenDependency])


object ArtifactCoordinates {
  def apply(d: Dependency): ArtifactCoordinates = ArtifactCoordinates(d.group, d.artifact, d.version)
}

case class ArtifactCoordinates(group: String,
                               artifact: String,
                               version: String) {
  override def toString: String = s"$group:$artifact:$version"
}

