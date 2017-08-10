package org.clulab.wikipedia

import scala.annotation.tailrec
import scala.util.matching.Regex


/**
  * replacement rules for text cleanup.  Convenience wrapper to handle partially applied replaceAllin
  */
case class ReplacementRule(pattern: Regex, replaceWith: String) {

  def applyReplacement(text: String): String = pattern.replaceAllIn(text, replaceWith)

}

/**
  * Utilties for parsing and applying ReplacementRules
  */
object ReplacementRule {

  /**
    * rules file: pattern -> replaceWith
    * @param rules
    * @return
    */
  def parse(rules: String): List[ReplacementRule] = {
    val res = rules.split("\n").flatMap {

      case comment if comment.startsWith("#") => Nil
      case emptyLine if emptyLine.trim.isEmpty => Nil
      case rule =>
        val components = rule.split(" -> ", 2) // split at the first occurrence
        // ensure rule appears valid
        if (components.length != 2) {
          throw new Exception(s"rule '$rule' is improperly formatted")
        } else {
          val pattern = new Regex(components.head.trim)
          val replaceWith = components.last.trim
          Seq(ReplacementRule(pattern, replaceWith))
        }
    }
    res.toList
  }

  @tailrec
  def applyRules(text: String, rules: List[ReplacementRule]): String = rules match {

    case noRemaining if noRemaining.isEmpty => text
    case nextRule :: remaining =>
      val res = nextRule.applyReplacement(text)
      applyRules(res, remaining)
    //rules.foldLeft(text) { (transformedText, rule) => rule.applyReplacement(transformedText) }
  }

  def applyRulesInParallel(text: String, rules: List[ReplacementRule], nThreads: Int): String = {
    val parLines = WikipediaParser.parallelizeIndexedLines(text, nThreads)
    parLines
      // apply rules to each line
      .map { pair =>
        val res = applyRules(pair._1, rules)
        (pair._2, res)
      }
      // recover seq.
      .seq
      // sort by index
      .sortWith(_._1 < _._1)
      // keep only the transformed text (discard index)
      .map(_._2)
      // join lines
      .mkString("\n")
  }

}