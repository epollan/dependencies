package dependencies

import java.io.PrintWriter
import java.util.concurrent.atomic.AtomicInteger

import dependencies.parsing.{Dependency, GradleParser, ParseLogging, Parser}
import dependencies.printing._

object CLI extends App {

  // Initial state
  private var maxDepth: Option[Int] = None
  private var conf: Option[String] = None
  private var repoServerName = "spredfast"
  private var parserFactory: (Iterator[String] => Parser) = GradleParser
  private var printerFactory: (PrintWriter => DependencyPrinter) = RawPrinter
  private val arg = new AtomicInteger(0)

  private def usage() {
    println(
      s"""Usage: <dependency list command> | java -jar target/scala-2.11/*one-jar.jar [options]")
         |  --verbose/-v                                    flag that triggers verbose parsing
         |  --max-depth/-d DEPTH                            max dependency 'depth', defaults to $maxDepth"
         |  --configuration/-c CONFIG                       specific configuration from which dependencies should be taken
         |  --format/-f (gradle)                            dependency listing format, defaults to gradle
         |  --output/-o (raw|bzl|bazel|bzl-file|bazel-file) output format, defaults to 'raw'.
         |                                                  'bzl' writes to stdout/stderr;
         |                                                  'bzl-file' writes to BUILD/WORKSPACE/etc.
         |  --repo/-r REPO_SERVER                           name of the Maven repo server, defaults to 'spredfast'
         |""".stripMargin)
    sys.exit(1)
  }

  private def shift(): String = args(arg.incrementAndGet())

  while (arg.get() < args.length) {
    args(arg.get()) match {
      case "--configuration" | "-c" =>
        conf = Some(shift())
      case "--depth" | "-d" =>
        maxDepth = Some(Integer.parseInt(shift()))
      case "--format" | "-f" =>
        val f = shift()
        f match {
          case "gradle" => parserFactory = GradleParser
          case _ => println(s"Unknown dependency format: $f"); usage()
        }
      case "--output" | "-o" =>
        val f = shift()
        f match {
          case "csv" => printerFactory = GoogleDocsCsvPrinter
          case "confluence" => printerFactory = ConfluencePrinter
          case "raw" => printerFactory = RawPrinter
          case "bzl" | "bazel" => printerFactory = (lines) => BazelPrinter(lines, repoServerName)
          case "bzl-file" | "bazel-file" => printerFactory = (lines) => BazelPrinter(lines, repoServerName, writeFiles = true)
          case _ => println(s"Unknown output format: $f"); usage()
        }
      case "--verbose" | "-v" =>
        ParseLogging.verbose = true
      case "--repo" | "-r" =>
        repoServerName = shift()
      case "--help" | "-h" | _ => usage()
    }
    arg.incrementAndGet()
  }

  val printer = printerFactory(new PrintWriter(System.out))
  printer.printHeader()

  // Parser produces tokens -- for now, we just care about matching Dependency tokens
  parserFactory(io.Source.stdin.getLines())
    .map[Option[Dependency]]({
      case d: Dependency if (maxDepth.isEmpty || d.depth <= maxDepth.get) && (conf.isEmpty || conf.get.equals(d.configuration.name)) =>
        Some(d)
      case _ =>
        None
    })
    .flatten
    .toSeq
    .sorted
    .foreach(d => printer.print(d))

  printer.close()
}