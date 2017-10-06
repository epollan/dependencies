package dependencies

import dependencies.model._
import dependencies.parsing.Dependency

/**
  * Given a series of parsed dependencies from a build tool, construct the proper transitive
  * dependency graph of a composite project (i.e. a project comprising potentially multiple, inter-dependent
  * sub-projects).
  *
  * NOTE: I'm pretty sure both the graph construction and flattening/deduping algorithms used
  * here would stack overflow if they encountered a cyclic graph.  I'm not sure you can get
  * into that state with, e.g., Gradle output, though...
  */
object ModelFactory {

  private type Deps = Map[ArtifactCoordinates, Set[ArtifactCoordinates]]

  /**
    * Turn a sequence of dependencies parsed from another build tools' output into
    * project-grouped [[MavenDependencies]].
    */
  def forParseResult(dependencies: Seq[Dependency]): MavenDependencies = {

    def mavenDependency(coordinates: ArtifactCoordinates, dependentToDependencies: Deps): MavenDependency = {
      MavenDependency(
        coordinates,
        dependentToDependencies.getOrElse(coordinates, Set())
            // recurse and create MavenDependencies for this dependency's first-degree transitive dependencies
            .map(mavenDependency(_, dependentToDependencies))
      )
    }

    val dependentToDependencies: Deps =
      dependencies
          .filter(_.neededBy.isDefined)
          .groupBy(_.neededBy.get.coordinates)
          // dedupe each dependent's dependencies' coordinates
          .mapValues(_.map(_.coordinates).toSet)

    /**
      * Root -- or "direct" -- dependencies of a module
      */
    val roots: Set[(ProjectCoordinates, MavenDependency)] = dependencies
        .filter(_.neededBy.isEmpty)
        .map(d => (ProjectCoordinates(d.configuration.project.name), d.coordinates))
        .map(t => (t._1, mavenDependency(t._2, dependentToDependencies)))
        .toSet

    /**
      * Set of all unique third-party dependencies in the entire transitive dependency graph for all projects.
      * Take this opportunity to also squash dependencies for the same artifact at different versions
      * down to a single version (the highest).
      */
    val thirdParty: Set[MavenDependency] = {
      def flattenDependencies(root: MavenDependency): Seq[MavenDependency] =
        if (root.coordinates.isInstanceOf[MavenArtifactCoordinates]) {
          Seq(root) ++ root.dependsOn.toSeq.flatMap(flattenDependencies)
        } else {
          root.dependsOn.toSeq.flatMap(flattenDependencies)
        }

      roots.flatMap(t => flattenDependencies(t._2))
          .groupBy(d => d.coordinates match {
            case m: MavenArtifactCoordinates => m.artifact
            case _ => throw new IllegalStateException("Should not see non-maven coordinates here: " + d.coordinates)
          })
          .values
          .flatMap(versionedDependencies => versionedDependencies.size match {
            case 0 => Seq()
            case 1 => Seq(versionedDependencies.head)
            case _ => Seq(versionedDependencies.maxBy(_.coordinates.asInstanceOf[MavenArtifactCoordinates].semanticVersion))
          })
          .toSet
    }

    MavenDependencies(
      roots.groupBy(_._1).mapValues(_.map(_._2)),
      thirdParty
    )
  }
}


