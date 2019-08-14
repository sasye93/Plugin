package loci.containerize.IO

import java.nio.file.Path

import scala.util.Try
import scala.util.parsing.json.JSON
import loci.containerize.main.Containerize
import loci.containerize.Check

class ContainerConfig[+C <: Containerize](json : Path)(implicit io : IO, implicit private val plugin : C) {

  private val config : String = io.readFromFile(json)
  private val parsed = JSON.parseFull(config)

  private val map : Map[Any, Any] = parsed match {
    case Some(e : Map[Any, Any]) => e
    case None => plugin.logger.error(s"Parsing JSON config for entry point failed, path: ${ json.toString }. Invalid syntax?"); null
  }

  plugin.logger.info("config " + map.toString)
  private def getValueOfKey(key : String) : Option[Any] = Check ?=>[Option[Any]] (this.map, this.map.get(key), None)
  private def parseString(v : Any) : Option[String] = Try { v.toString }.toOption
  //def parseInt(v : Any) : Option[Int] = Try { parseString(v).get.toInt }.toOption
  private def parseDouble(v : Any) : Option[Double] = Try { parseString(v).get.toDouble }.toOption
  private def parseBoolean(v : Any) : Option[Boolean] = Try { parseString(v).get.toBoolean }.toOption

  private def getStringOfKey(key : String) : Option[String] = getValueOfKey(key) match{ case Some(s) => parseString(s); case _ => None }
  //def getIntOfKey(key : String) : Option[Int] = getValueOfKey(key) match{ case Some(s) => parseInt(s); case _ => None }
  private def getDoubleOfKey(key : String) : Option[Double] = getValueOfKey(key) match{ case Some(s) => parseDouble(s); case _ => None }
  private def getBooleanOfKey(key : String) : Option[Boolean] = getValueOfKey(key) match{ case Some(s) => parseBoolean(s); case _ => None }


  def getIsPublic : Option[Boolean] = getBooleanOfKey("public")

}
