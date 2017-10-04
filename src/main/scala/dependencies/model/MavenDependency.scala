package dependencies.model

case class MavenDependency(coordinates: ArtifactCoordinates,
                           dependsOn: Set[MavenDependency])
    extends Ordered[MavenDependency] {
  override def compare(that: MavenDependency): Int = coordinates.compare(that.coordinates)
}
