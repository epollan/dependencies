package dependencies.model

import java.nio.file.{Path, Paths}
import java.util.Comparator
import java.util.regex.Pattern

import com.google.common.base.Strings
import com.google.common.collect.ComparisonChain

abstract class ArtifactCoordinates extends Ordered[ArtifactCoordinates] {

  val bazelName: String
  val packageQualifiedBazelName: String

  override def compare(that: ArtifactCoordinates): Int = ComparisonChain.start()
      .compare(getClass.getSimpleName, that.getClass.getSimpleName)
      .compare(toString, that.toString)
      .result()
}


object SemanticVersion {
  private val majorMinorPatch = Pattern.compile("(?<major>[^\\.]+)(\\.(?<minor>[^\\.]+)(\\.(?<patch>.*))?)?")

  def apply(version: String): SemanticVersion = {
    val matcher = majorMinorPatch.matcher(version)
    if (matcher.matches()) {
      SemanticVersion(
        matcher.group("major"),
        Strings.nullToEmpty(matcher.group("minor")),
        Strings.nullToEmpty(matcher.group("patch"))
      )
    } else {
      System.err.println(s"Couldn't cleanly parse semantic version: $version")
      SemanticVersion(version, "", "")
    }
  }

  private def comparatorForField(parsedGetter: (SemanticVersion) => Option[java.lang.Long],
                                 rawGetter: (SemanticVersion) => String): Comparator[SemanticVersion] = {
    new Comparator[SemanticVersion] {
      override def compare(o1: SemanticVersion, o2: SemanticVersion): Int =
        parsedGetter(o1).map(v => v.compareTo(parsedGetter(o2).getOrElse(-1L)))
            .orElse(Some(rawGetter(o1).compareTo(rawGetter(o2))))
            .get
    }
  }
}


case class SemanticVersion(major: String, minor: String, patch: String)
    extends Ordered[SemanticVersion] {

  private lazy val majorVersionNumber = parse(major)
  private lazy val minorVersionNumber = parse(minor)
  private lazy val patchVersionNumber = parse(patch)

  override def compare(that: SemanticVersion): Int = ComparisonChain.start()
      .compare(this, that, SemanticVersion.comparatorForField(_.majorVersionNumber, _.major))
      .compare(this, that, SemanticVersion.comparatorForField(_.minorVersionNumber, _.minor))
      .compare(this, that, SemanticVersion.comparatorForField(_.patchVersionNumber, _.patch))
      .result()

  private def parse(versionString: String): Option[java.lang.Long] = try {
    Some(java.lang.Long.parseLong(versionString))
  } catch {
    case _: NumberFormatException => None
  }
}


sealed case class MavenArtifact(groupId: String, artifactId: String)


sealed case class MavenArtifactCoordinates(artifact: MavenArtifact, version: String)
    extends ArtifactCoordinates {

  val semanticVersion: SemanticVersion = SemanticVersion.apply(version)

  override lazy val bazelName: String = (
      artifact.groupId.split("[-\\.]").toList ++
          artifact.artifactId.split("[-\\.]").toList
  ).mkString("_")

  override lazy val packageQualifiedBazelName: String = "//third_party:" + bazelName

  override def toString: String = s"${artifact.groupId}:${artifact.artifactId}:$version"
}


sealed case class ProjectCoordinates(name: String)
    extends ArtifactCoordinates {

  override lazy val bazelName: String = s":$name"

  override lazy val packageQualifiedBazelName: String = s"//${relativeProjectPath.toString}:$name"

  /**
    * Naive, brute-force way to map a project to a project directory.  We have a tendency to name
    * projects like {{{foo-bar-baz}}}.  Unfortunately, we don't standardize on corresponding project
    * directory structures.  Look in places like {{{foo/bar-baz}}}, {{{foo/bar/baz}}}, and {{{foo-bar/baz}}}.
    */
  lazy val relativeProjectPath: Path = {
    var projectDir = Paths.get(name)
    val sep = "-"

    if (!projectDir.toFile.isDirectory && name.contains(sep)) {
      // foo-bar-baz-xxx project may be in /foo-bar-baz-xxx, but could also be /foo/bar/baz/xxx, /foo/bar/baz-xxx, /foo/bar-baz/xxx, ...
      val segments = name.split(sep).toList

      val possibleProjectDirs: Set[Path] =
      // /foo/bar/baz/xxx
        Set(Paths.get(segments.head, segments.tail: _*)) ++
            // leave tail segments whole: /foo/bar/baz-xxx, /foo/bar-baz-xxx
            (1 until segments.size).map(segmentCount => {
              val (middle, tail) = (segments.tail.dropRight(segmentCount), List(segments.tail.takeRight(segmentCount).mkString(sep)))
              Paths.get(segments.head, (middle ++ tail): _*)
            }) ++
            // leave head segments whole: /foo-bar/baz/xxx, /foo-bar-baz/xxx
            (1 until segments.size).map(segmentCount =>
              Paths.get(segments.take(segmentCount).mkString(sep), segments.slice(segmentCount, segments.size): _*)
            ) ++
            // Just partition the segments a head and tail of varying lengths: /foo/bar-baz-xxx, /foo-bar/baz-xxx, /foo-bar-baz/xxx
            (1 until segments.size).map(segmentCount =>
              Paths.get(segments.take(segmentCount).mkString(sep), segments.slice(segmentCount, segments.size).mkString(sep))
            ) ++
            // slide an N-segment window across the segments: /foo-bar/baz/xxx, /foo/bar-baz/xxx, /foo/bar/baz-xxx (2);
            // /foo-bar-baz/xxx, /foo/bar-baz-xxx (3)
            (2 until segments.size).flatMap(windowSize => {
              (0 until segments.size - windowSize).map(pos => {
                val head = segments.take(pos)
                val window = segments.drop(pos).take(windowSize)
                val tail = segments.drop(pos + windowSize)
                val windowedSegments = head ++ List(window.mkString(sep)) ++ tail
                Paths.get(windowedSegments.head, windowedSegments.tail: _*)
              })
            })
      projectDir = possibleProjectDirs
          .view
          .collectFirst { case path if path.toFile.isDirectory => path }
          .orElse(Some(projectDir))
          .get
    }
    projectDir
  }
}
