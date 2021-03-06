package com.getjenny.analyzer.atoms

/**
  * Created by mal on 20/02/2017.
  */

import com.getjenny.analyzer.interfaces._

class DefaultFactoryAtomic extends Factory[String, AbstractAtomic] {

  override val operations = Set("keyword" , "similar", "synonym", "regex")

  override def get(name: String, argument: String): AbstractAtomic = name.filter(c => !c.isWhitespace ) match {
    case "keyword" => new KeywordAtomic(argument)
    case "regex" => new RegularExpressionAtomic(argument)
    case _ => throw ExceptionAtomic("Atom \'" + name + "\' not found")
  }
}