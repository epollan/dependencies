package dependencies.model

/**
  * @param roots Each project has a set of root/first-degree dependencies
  * @param thirdParty a flattened view of all projects' transitive third party dependencies
  */
case class MavenDependencies(roots: Map[ProjectCoordinates, Set[MavenDependency]],
                             thirdParty: Set[MavenDependency])

