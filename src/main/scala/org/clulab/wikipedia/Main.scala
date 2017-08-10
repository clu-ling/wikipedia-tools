package org.clulab.wikipedia

import java.io.File

import com.typesafe.scalalogging.LazyLogging


/**
  * Defines CLI runnables.
  */
object Main extends App with LazyLogging {

  // commands
  val EXTRACT_TEXT_FROM_WIKI = "extract-text-from-wiki"

  // create command-line arguments parser
  val parser = new scopt.OptionParser[Config]("wikipedia-to-text") {

    head("Wikipedia-to-text")
    help("help") text("display this message")

    note("Retrieve a wikipedia dump (ex. wget http://dumps.wikimedia.org/enwiki/latest/enwiki-latest-pages-articles.xml.bz2)")

    cmd(EXTRACT_TEXT_FROM_WIKI)
      .action((_, c) => c.copy(command = Some(EXTRACT_TEXT_FROM_WIKI)))
      .text("extract text from wikipedia dump")
      .children(
        opt[String]("input").valueName("<path/to/file>") action { (inputFile, c) =>
          c.copy(inputFile = Some(inputFile))
        } text("the .xml.bz2 wikipedia dump"),
        opt[File]("output").valueName("<path/to/output/dir>") action { (outDir, c) =>
          c.copy(outDir = outDir)
        } text("the output directory for the cleaned text files"),
        opt[Int]("threads").valueName("<int>") action { (threads, c) =>
          c.copy(nThreads = threads)
        } text("the level of parallelization to use for segmentation and rule application"),
        opt[String]("lang").valueName("<lang-code>") action { (lang, c) =>
          c.copy(lang = WikipediaParser.DEFAULT_LANG)
        } text(s"language of dump (default is '${WikipediaParser.DEFAULT_LANG}')"),
        opt[Boolean]("keep-title").valueName("<bool>") action { (title, c) =>
          c.copy(withTitle = WikipediaParser.DEFAULT_KEEP_TITLE)
        } text(s"include section titles in output (default is ${WikipediaParser.DEFAULT_KEEP_TITLE})"),
        opt[Boolean]("keep-footer").valueName("<bool>") action { (footer, c) =>
          c.copy(withFooter = WikipediaParser.DEFAULT_KEEP_FOOTER)
        } text(s"include footer contents in output (default is ${WikipediaParser.DEFAULT_KEEP_FOOTER})"),
        opt[File]("rules").valueName("<path/to/transformation.rules>") action { (f, c) =>
          c.copy(rulesFile = Some(f))
        } text(s"custom transformation rules to apply to extracted text (default is resources${WikipediaParser.DEFAULT_RULES_FILE})"),
        opt[Boolean]("split-sentences") action { (splitSentences, c) =>
          c.copy(segmentSentences = WikipediaParser.DEFAULT_SPLIT_SENTENCES)
        } text(s"segment sentences (one sentence per line. default is ${WikipediaParser.DEFAULT_SPLIT_SENTENCES})"),
        checkConfig {
          case invalidLang if ! WikipediaParser.validLanguage(invalidLang.lang) =>
            failure(s"'${invalidLang.lang}' is not supported language. Options: ${WikipediaParser.SUPPORTED_LANGUAGES.toSeq.sorted}")
          case noInput if  noInput.inputFile.isEmpty =>
            failure("No input file was specified.")
          case noSuchFile if ! new File(noSuchFile.inputFile.get).exists() =>
            failure(s"'${noSuchFile.inputFile.get}' does not exist")
          case _ => success
        }
      )

    // a command must be specified
    checkConfig { c =>
      if (c.command.isDefined) success else {
        failure("A command is required.")
      }
    }

  }

  parser.parse(args, Config()) match {
    case None => sys.exit(1)
    case Some(config) => config.command.get match {
      case `EXTRACT_TEXT_FROM_WIKI` =>
        val parser = new WikipediaParser(config)
        // TODO: download dump if none provided?
        val wikiDump: String = config.inputFile.get
        // review input
        logger.info(s"inputFile:\t$wikiDump")
        logger.info(s"output dir:\t${config.outDir.getAbsolutePath}")
        logger.info(s"threads:\t${config.nThreads}")
        logger.info(s"keep titles?\t${config.withTitle}")
        logger.info(s"keep footers?\t${config.withFooter}")
        logger.info(s"segment sentences?\t${config.segmentSentences}")
        logger.info(s"rules:\t${if (config.rulesFile.nonEmpty) config.rulesFile.get.getAbsolutePath else WikipediaParser.DEFAULT_RULES_FILE}")
        // parse dump and save output
        println("")
        logger.info("Processing dump...")
        parser.parseDumpAndSave(new File(wikiDump))
      case cmd => ???
    }
  }
}
