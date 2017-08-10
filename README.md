# wikipedia-tools
tools for processing wikipedia (dumps to text, etc.)


## Convert a wikipedia dump into clean(-ish) plain text using the `extract-text-from-wiki` task

First, you'll want to build a fat jar.  This step requires [`sbt`](http://www.scala-sbt.org/1.0/docs/Setup.html).
```bash
# build a fat jar (requires sbt)
sbt assembly
```

To run the task you'll need [a dump of wikipedia](http://dumps.wikimedia.org/enwiki/latest/enwiki-latest-pages-articles.xml.bz2).  

Several options can be set to customize the behavior of the task (run the task with the `--help` flag to see a summary).

```bash
# see available options for running the command of interest
java -jar target/scala_2.11/<name>.jar extract-text-from-wiki --help
```

Here is one example of an invocation that processes articles in parallel.  To run this you'll need a wikipedia dump.

```bash
# this command will process 40 articles in parallel
# and redirect the program's log to a file (clean-wiki-dump.txt)
java -Xmx32G -jar target/scala_2.11/<name>.jar extract-text-from-wiki --input wikipedia-dump/enwiki-latest-pages-articles.xml.bz2 --threads 40 > clean-wiki-dump.txt
```
