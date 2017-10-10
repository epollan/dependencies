package dependencies.model

case class MavenDependency[C <: ArtifactCoordinates](coordinates: C,
                                                     dependsOn: Set[MavenDependency[C]])
    extends Ordered[MavenDependency[C]] {
  override def compare(that: MavenDependency[C]): Int = coordinates.compare(that.coordinates)
}
