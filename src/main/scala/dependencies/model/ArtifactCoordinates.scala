package dependencies.model

import com.google.common.collect.ComparisonChain

abstract class ArtifactCoordinates extends Ordered[ArtifactCoordinates] {
  val bazelName: String
  val packageQualifiedBazelName: String
  override def compare(that: ArtifactCoordinates): Int = ComparisonChain.start()
      .compare(getClass.getSimpleName, that.getClass.getSimpleName)
      .compare(toString, that.toString)
      .result()
}


sealed case class MavenArtifactCoordinates(group: String, artifact: String, version: String)
    extends ArtifactCoordinates {

  override lazy val bazelName: String = (
      group.split("[-\\.]").toList ++
          artifact.split("[-\\.]").toList
  ).mkString("_")

  override lazy val packageQualifiedBazelName: String = "//third_party:" + bazelName

  override def toString: String = s"$group:$artifact:$version"

}

sealed case class ProjectCoordinates(name: String)
    extends ArtifactCoordinates {

  override lazy val bazelName: String = s":$name"

  override lazy val packageQualifiedBazelName: String = s"//$name:$name"
}
