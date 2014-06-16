package dependencies.printing

import java.io.PrintWriter

import dependencies.parsing.Dependency

object DependencyPrinter {
	def confluence: PrintWriter => DependencyPrinter = w => new ConfluencePrinter(w)
	def csv: PrintWriter => DependencyPrinter = w => new GoogleDocsCsvPrinter(w)
	def raw: PrintWriter => DependencyPrinter = w => new RawPrinter(w)
}

abstract class DependencyPrinter(w: PrintWriter) {
	def printHeader()
	def print(d: Dependency)
	def close() = w.close()

	protected def versionDetailsUrl(d: Dependency): String =
		s"http://search.maven.org/#artifactdetails%7C${d.group}%7C${d.artifact}%7C${d.version}%7Cjar"

	protected def artifactVersionsUrl(d: Dependency): String =
		s"http://search.maven.org/#search%7Cgav%7C1%7Cg%3A%22${d.group}%22%20AND%20a%3A%22${d.artifact}%22"
}

final class RawPrinter(w: PrintWriter) extends DependencyPrinter(w) {
	override def printHeader() = {}
	override def print(d: Dependency) = w.println(d)
}

abstract class CsvPrinter(w: PrintWriter) extends DependencyPrinter(w) {
	override def printHeader() = w.println("Dependency,Version,License,Notes")

	override def print(d: Dependency) = {
		w.append('"')
			.append(escapeQuotes(hyperlink(artifactVersionsUrl(d), d.group + ":" + d.artifact)))
			.append("\", \"")
			.append(escapeQuotes(hyperlink(versionDetailsUrl(d), d.version)))
			.append("\", ,")
			.println()
	}

	def hyperlink(href: String, text: String): String

	private def escapeQuotes(s: String): String = s.replace("\"", "\"\"")
}

/**
 * Assuming Google docs hyperlink formatting
 */
final class GoogleDocsCsvPrinter(w: PrintWriter) extends CsvPrinter(w) {
	override def hyperlink(href: String, text: String): String = s"""=HYPERLINK("$href", "$text")"""
}

final class ConfluencePrinter(w: PrintWriter) extends DependencyPrinter(w) {
	override def printHeader() = w.println("||Dependency||Version||License||Notes||")

	override def print(d: Dependency) = {
		w.append("| [")
			.append(d.group)
			.append(":")
			.append(d.artifact)
			.append("|")
			.append(artifactVersionsUrl(d))
			.append("] | [")
			.append(d.version)
			.append("|")
			.append(versionDetailsUrl(d))
			.append("] | | |")
			.println()
	}
}