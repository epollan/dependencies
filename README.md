JVM Project Dependency Pretty-Printer
============

Parses both Gradle and Maven-style dependency tree output into a normalized form, and 
prints the resultant dependency information in a few useful formats.  Helpful to extract
dependency information for posting to Confluence or Google Docs

For example, to dump dependency information from a Maven project into a CSV that can be
imported into a Google Docs spreadsheet:

	cd <project> && \
		mvn dependency:tree | \
		java -jar <dependencies dir>/target/scala-2.10/*one-jar.jar -d 0 -c compile > import.csv

Building
--------

This is a Scala project that uses SBT.  To create an executable jar file:

	sbt one-jar

Running
-------

The tool reads dependency data from standard input, on the expectation that you'd 
pipe in either the output from `mvn dependency:tree` or `gradle dependencies`.

Command usage can be printed with the `-h` option:

	[.../dependencies (master)]$ java -jar target/scala-2.10/*one-jar.jar -h
	Usage: <dependency list command> | java -jar target/scala-2.10/*one-jar.jar [options]
	  --max-depth/-d                   max dependency 'depth', defaults to 0
	  --configuration/-c               specific configuration from which dependencies should be taken
	  --format/-f (gradle|maven)       dependency listing format, defaults to gradle
	  --output/-o (csv|confluence|raw) output format, defaults to CSV
