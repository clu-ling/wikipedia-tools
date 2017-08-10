package org.clulab.wikipedia

import java.io._

import collection.JavaConverters._
import ai.lum.common.FileUtils._
import ai.lum.common.IteratorUtils._
import com.typesafe.scalalogging.LazyLogging
import org.clulab.processors.{ Processor, Sentence }
import org.clulab.processors.clu.CluProcessor
import org.wikiclean.{ WikiClean, WikipediaArticlesDump }
import org.wikiclean.WikiClean.WikiLanguage

import scala.collection.parallel.{ ForkJoinTaskSupport, ParSeq }
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future


/**
  * Config used by runnable
  */
case class Config(
  command: Option[String] = None,
  nThreads: Int = 1,
  inputFile: Option[String] = None, // ex. "enwiki-latest-pages-articles.xml.bz2"
  outDir: File = new File("output"),
  lang: String = "en", // english, german (de), or chinese (zh)
  lowerCase: Boolean = false,
  withTitle: Boolean = true,
  withFooter: Boolean = false,
  segmentSentences: Boolean = true,
  rulesFile: Option[File] = None
)

/**
  * Representation of a wikipedia article
  */
case class Article(title: String, id: String, text: String)


/**
  * Extract text from wikipedia dump
  */
class WikipediaParser(config: Config) extends WikiClean with LazyLogging {

  import WikipediaParser._

  override val withTitle = config.withTitle
  override val withFooter = config.withFooter
  val lang = WikipediaParser.mapLang(config.lang)

  lazy val proc: Processor = new CluProcessor()

  val cleaner: WikiClean = {
    new WikiClean.Builder()
      .withLanguage(lang)
      .withTitle(config.withTitle)
      .withFooter(config.withFooter).build()
  }

  /**
    * Clean the text contents of an article as specified in the config
    */
  def cleanArticle(article: String): Article = {


    val title = cleaner.getTitle(article)
    val articleId = cleaner.getId(article)

    //def maybeRemoveFooter(txt: String): String = if (! config.withFooter) removeFooters(txt, lang) else txt

    // check if text should be sentence segmented
    def maybeSegment(txt: String): String  = if (config.segmentSentences) segmentSentences(txt) else txt

    def maybeCaseFold(txt: String): String  = if (config.lowerCase) txt.toLowerCase else txt

    def runRules(txt: String): String  = applyRules(txt, config.rulesFile, config.lang)

    def attemptHTMLEscape(txt: String): String = {
      // according to notes in wikiclean, some of the articles are doubly html encoded
      // This sometimes fails within wikiclean, so it should be wrapped in a Try
      ???
    }

    val pipeline = List[(String) => String](
      // currently, this can't be customized
      cleaner.clean,
      maybeSegment,
      // apply postprocessing rules
      runRules,
      maybeCaseFold
    )

    val clean: String = Function.chain(pipeline)(article)

    Article(title = title, id = articleId, text = clean)

  }

  /**
    * Get a Scala Iterator of articles contained in a compressed wikipedia dump
    */
  def getArticles(file: File): Iterator[String] = {

    val wd = new WikipediaArticlesDump(file)
    wd.iterator().asScala

  }

  /**
    * Process a wikipedia dump (compressed file) and save the clean text output of each article to a separate file.
    */
  def parseDumpAndSave(file: File): Unit = {

    config.outDir.mkdirs()

    val articles = getArticles(file).par

    articles.tasksupport = new ForkJoinTaskSupport(new scala.concurrent.forkjoin.ForkJoinPool(config.nThreads))

    articles.foreach { rawText =>
      try {
        val article = cleanArticle(rawText)
        val outFile = new File(config.outDir, s"${article.id}-${article.title}.txt")
        outFile.writeString(article.text, java.nio.charset.StandardCharsets.UTF_8, append = false)
        logger.debug(s"Processed ${outFile.getName}")
      } catch {
        case e: Exception =>
          val articleId = cleaner.getId(rawText)
          var title = cleaner.getTitle(rawText)
          logger.error(s"error processing article $articleId ($title)")
      }

    }

  }

  /**
    * One sentence per line, where words are delimited by whitespace
    */
  def sentencesToText(sentences: Array[Sentence]): String = {
    sentences.map(_.words.mkString(" ")).mkString("\n")
  }

  /**
    * Segment text into sentences
    */
  def segmentSentences(text: String): String = {
    val doc = proc.mkDocument(text)
    sentencesToText(doc.sentences)
  }

  /**
    * Parallelize the segmentation of sentences, but retain the original order of the text
    */
  def segmentSentencesInParallel(text: String): String = {
    val lines = parallelizeIndexedLines(text, config.nThreads)
    lines
      // segment each line
      .map { pair =>
        val res = proc.mkDocument(pair._1)
        (pair._2, res)
      }
      // recover seq.
      .seq
      // sort by index
      .sortWith(_._1 < _._1)
      // keep only the transformed text (discard index)
      .flatMap { p =>
        val doc = p._2
        sentencesToText(doc.sentences)
      }
      // join sentence chunks
      .mkString("\n")
  }

  /**
    * Apply replacement rules in order
    */
  def applyRules(text: String, rulesFile: Option[File], lang: String): String = {
    val rules = getRules(rulesFile, lang)
    ReplacementRule.applyRules(text, rules)
  }

  /**
    * Process lines in parallel, while maintaining the application order of replacement rules.
    */
  def applyRulesInParallel(text: String, rulesFile: Option[File], lang: String): String = {
    val rules = getRules(rulesFile, lang)
    // apply rules in parallel to each line
    ReplacementRule.applyRulesInParallel(text, rules, config.nThreads)
  }

}


/**
  * Companion object to WikipediaParser.
  * Defines utilities commonly used by instances.
  */
object WikipediaParser extends LazyLogging {

  val DEFAULT_KEEP_TITLE = true
  val DEFAULT_KEEP_FOOTER = false
  val DEFAULT_SPLIT_SENTENCES = true
  val DEFAULT_RULES_FILE = "/org/clulab/wikipedia/rules/en.rules"

  val DEFAULT_LANG = "en"

  val SUPPORTED_LANGUAGES = Set("en")//, "de", "zh")

  def validLanguage(lang: String): Boolean = SUPPORTED_LANGUAGES contains lang.toLowerCase

  /**
    * Used to map from a language code string to wikiclean's representation
    */
  def mapLang(lang: String): WikiLanguage = lang.toLowerCase match {
    case "en" => WikiLanguage.EN
//    case "de" => WikiLanguage.DE
//    case "zh" => WikiLanguage.ZH
    case other =>
      throw new Exception(s"Unsupported language '$other'")
  }

  /**
    * load rules from a file (if provided), or fall back to predefined rules for the specified language code
    */
  def getRules(rulesFile: Option[File], lang: String): List[ReplacementRule] = {
    // was a rules file provided?
    if (rulesFile.nonEmpty) {
      ReplacementRule.parse(rulesFile.get.readString())
    } else { getRules(lang) }
  }

  /**
    * Load predefined rules for the specified language code. <br>
    * An invalid language code will result in an empty list of ReplacementRules.
    */
  def getRules(lang: String): List[ReplacementRule] = {

    def streamToString(stream: InputStream): String = {
      scala.io.Source.fromInputStream(stream).getLines().mkString("\n")
    }

    val path = "/org/clulab/wikipedia/rules"
    val rawRules: String = if ( ! validLanguage(lang) ) {
      logger.warn(s"No rules for language '$lang'")
      ""
    } else {
        val stream = scala.io.Source.getClass.getResourceAsStream(s"$path/${lang.toLowerCase}.rules")
        streamToString(stream)
    }

    ReplacementRule.parse(rawRules)
  }

  /**
    * Split text on newlines and create a thread-limited parallel collection
    * where each line remembers its original order (index)
    */
  def parallelizeIndexedLines(text: String, nThreads: Int): ParSeq[(String, Int)] = {

    val parLines = {
      text
        // process line by line
        .split("\n")
        // retain index for later sorting
        .zipWithIndex
        // process in parallel
        .par
    }

    parLines.tasksupport = new ForkJoinTaskSupport(new scala.concurrent.forkjoin.ForkJoinPool(nThreads))

    parLines
  }

}