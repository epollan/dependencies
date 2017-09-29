package dependencies

import java.io.PrintWriter
import java.util.concurrent.atomic.AtomicInteger

import dependencies.parsing.{Dependency, GradleParser, Parser}
import dependencies.printing._

object CLI extends App {

  // Initial state
  private var maxDepth = 0
  private var conf: Option[String] = None
  private var parserFactory: (Iterator[String] => Parser) = GradleParser
  private var printerFactory: (PrintWriter => DependencyPrinter) = RawPrinter
  private val arg = new AtomicInteger(0)

  private def usage() {
    println("Usage: <dependency list command> | java -jar target/scala-2.11/*one-jar.jar [options]")
    println(s"  --max-depth/-d                             max dependency 'depth', defaults to $maxDepth")
    println(s"  --configuration/-c                         specific configuration from which dependencies should be taken")
    println(s"  --format/-f (gradle|maven)                 dependency listing format, defaults to gradle")
    println(s"  --output/-o (csv|confluence|raw|bzl|bazel) output format, defaults to 'raw'")
    sys.exit(1)
  }

  private def shift(): String = args(arg.incrementAndGet())

  while (arg.get() < args.length) {
    args(arg.get()) match {
      case "--configuration" | "-c" =>
        conf = Some(shift())
      case "--depth" | "-d" =>
        maxDepth = Integer.parseInt(shift())
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
          case "bzl" | "bazel" => printerFactory = BazelPrinter
          case _ => println(s"Unknown output format: $f"); usage()
        }
      case "--help" | "-h" | _ => usage()
    }
    arg.incrementAndGet()
  }

  val printer = printerFactory(new PrintWriter(System.out))
  printer.printHeader()

  // Parser produces tokens -- for now, we just care about matching Dependency tokens
  parserFactory(io.Source.stdin.getLines())
    .map[Option[Dependency]]({
      case d: Dependency if d.depth <= maxDepth && (conf.isEmpty || conf.get.equals(d.configuration.name)) =>
        Some(d)
      case _ =>
        None
    })
    .flatten  // eliminate None and extract raw Dependencies
    .toSet    // unique-ify
    .toSeq
    .sorted
    .foreach(d => printer.print(d))

  printer.close()
}