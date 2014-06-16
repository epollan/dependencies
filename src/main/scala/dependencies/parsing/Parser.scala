package dependencies.parsing

import java.util.regex.Pattern

abstract class Parser extends Iterator[DependencyTreeToken] {
	protected var currentToken: Option[DependencyTreeToken] = None

	def hasNext: Boolean = {
		currentToken match {
			case Some(End()) => false
			case _ => true
		}
	}

	def next(): DependencyTreeToken = {
		if (currentToken eq Some(End())) {
			throw new IllegalStateException("parser reached end of stream")
		}
		currentToken = consumeNextToken().orElse(Some(End()))
		currentToken.get
	}

	protected def consumeNextToken(): Option[DependencyTreeToken]
}

object Parser {
	def maven: (Iterator[String] => Parser) = lines => MavenParser(lines)
	def gradle: (Iterator[String] => Parser) = lines => GradleParser(lines)
}

final case class MavenParser(lines: Iterator[String]) extends Parser {

	protected def consumeNextToken(): Option[DependencyTreeToken] = {
		currentToken match {
			case None =>
				return Some(Preamble())

			case Some(Preamble()) | Some(Dependency(_, _, _, _, _)) =>
				while (lines.hasNext) {
					val d = MavenDependency.parse(lines.next())
					if (d.isDefined) {
						return d
					}
				}

			case _ => throw new IllegalStateException("Unexpected parse state")
		}
		None
	}

	private object MavenDependency {
		val pattern = Pattern.compile("(?:\\[[A-Z]+\\] )" +
			"(?<indent>([\\|+\\\\][- ]{2})*)" +
			"(?<group>[\\S\\.]+):" +
			"(?<artifact>[\\S\\.]+):" +
			"(?:[\\S\\.]+):" +
			"(?<version>[\\S\\.]+):" +
			"(?<scope>[\\S\\.]+)"
		)
		private val indention = Pattern.compile("(\\|  )")

		def parse(line: String): Option[Dependency] = {
			val matcher = pattern.matcher(line)
			if (matcher.matches()) {
				return Some(Dependency(
					Configuration(matcher.group("scope")),
					depth(matcher.group("indent")),
					matcher.group("group"),
					matcher.group("artifact"),
					matcher.group("version"))
				)
			}
			None
		}

		private def depth(indent: String) = {
			var depth = 0
			val matcher = indention.matcher(indent)
			while (matcher.find()) {
				depth += 1
			}
			depth
		}
	}
}

final case class GradleParser(lines: Iterator[String]) extends Parser {

	protected def consumeNextToken(): Option[DependencyTreeToken] = {
		currentToken match {
			case None =>
				return Some(Preamble())

			case Some(Preamble()) =>
				while (lines.hasNext) {
					val configuration = GradleConfiguration.parse(lines.next())
					if (configuration.isDefined) {
						return configuration
					}
				}

			case Some(Configuration(c)) =>
				while (lines.hasNext) {
					val line = lines.next()
					val state = GradleDependency.parse(Configuration(c), line)
						.orElse(GradleConfiguration.parse(line))
					if (state.isDefined) {
						return state
					}
				}

			case Some(Dependency(conf, indent, group, artifact, version)) =>
				while (lines.hasNext) {
					val line = lines.next()
					val state = GradleDependency.parse(conf, line)
						.orElse(GradleConfiguration.parse(line))
					if (state.isDefined) {
						return state
					}
				}

			case _ => throw new IllegalStateException("Unexpected parse state")
		}
		None
	}

	private object GradleConfiguration {
		private val pattern = Pattern.compile("(\\S+) - .*")

		def parse(line: String): Option[Configuration] = {
			val matcher = pattern.matcher(line)
			if (matcher.matches()) {
				return Some(Configuration(matcher.group(1)))
			}
			None
		}
	}

	private object GradleDependency {
		private val pattern = Pattern.compile("(?<indent>([\\|+\\\\][- ]{4})*)" +
			"(?<group>[\\S\\.]+):" +
			"(?<artifact>[\\S\\.]+):" +
			"(?<version>[^\\(\\*\\)]*)"
		)
		private val indention = Pattern.compile("(\\|    )")
		private val upgrade = Pattern.compile("(?:[^ \\->]*)(?: -> )(?<to>[^ \\->]*)")

		def parse(conf: Configuration, line: String): Option[Dependency] = {
			val matcher = pattern.matcher(line)
			if (matcher.matches()) {
				var version = matcher.group("version")
				val upgradeMatcher = upgrade.matcher(version)
				if (upgradeMatcher.matches()) {
					version = upgradeMatcher.group("to")
				}
				return Some(Dependency(
					conf,
					depth(matcher.group("indent")),
					matcher.group("group"),
					matcher.group("artifact"),
					version)
				)
			}
			None
		}

		private def depth(indent: String) = {
			var depth = 0
			val matcher = indention.matcher(indent)
			while (matcher.find()) {
				depth += 1
			}
			depth
		}
	}
}
