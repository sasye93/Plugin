package loci.containerize.IO

import java.io.File
import java.nio.file.{Path, Paths}

import scala.util.Try
import scala.util.parsing.json.JSON
import loci.containerize.main.Containerize
import loci.containerize.Options
import loci.containerize.Check

class ContainerConfig[+C <: Containerize](json : File)(implicit io : IO, implicit private val plugin : C) {

  def getConfigType : String = if(Check ? json) s"custom config: ${ json.getName }" else "default config"

  //todo check and document if this works
  private val config : String = {
    if(Check ? json) io.readFromFile(json)
    else{
      Options.getSetupConfig(plugin.logger) match{
        case Some(f) => io.readFromFile(f)
        case None => Options.defaultConfig.JSON
      }
    }
  }
  private val parsed = JSON.parseFull(config)

  private val map : Map[Any, Any] = parsed match {
    case Some(e : Map[Any, Any]) => e
    case None => plugin.logger.error(s"Parsing JSON config for entry point failed, path: ${ if(Check ? json) json.toString else "- default options (please supply a config file)" }. Invalid syntax?"); null
  }

  private def getValueOfKey(key : String) : Option[Any] = Check ?=>[Option[Any]] (this.map, this.map.get(key), None)
  private def parseString(v : Any) : Option[String] = Try { v.toString }.toOption
  //def parseInt(v : Any) : Option[Int] = Try { parseString(v).get.toInt }.toOption
  private def parseDouble(v : Any) : Option[Double] = Try { parseString(v).get.toDouble }.toOption
  private def parseBoolean(v : Any) : Option[Boolean] = Try { parseString(v).get.toBoolean }.toOption

  private def getStringOfKey(key : String) : Option[String] = getValueOfKey(key) match{ case Some(s) => parseString(s); case _ => None }
  //def getIntOfKey(key : String) : Option[Int] = getValueOfKey(key) match{ case Some(s) => parseInt(s); case _ => None }
  private def getDoubleOfKey(key : String) : Option[Double] = getValueOfKey(key) match{ case Some(s) => parseDouble(s); case _ => None }
  private def getBooleanOfKey(key : String) : Option[Boolean] = getValueOfKey(key) match{ case Some(s) => parseBoolean(s); case _ => None }

  def getIsPublic : Boolean = getBooleanOfKey("public").getOrElse(Options.defaultConfig.public)
  def getReplicas : Integer = Math.ceil(getDoubleOfKey("replicas").getOrElse(Options.defaultConfig.replicas)).toInt
  def getCPULimit : Double = getDoubleOfKey("cpu_limit").getOrElse(Options.defaultConfig.cpu_limit)
  def getCPUReserve : Double = getDoubleOfKey("cpu_reserve").getOrElse(Options.defaultConfig.cpu_reserve)
  def getMemLimit : String = getStringOfKey("memory_limit").getOrElse(Options.defaultConfig.memory_limit)
  def getMemReserve : String = getStringOfKey("memory_reserve").getOrElse(Options.defaultConfig.memory_reserve)
  def getDeployMode : String = getStringOfKey("deploy_mode").getOrElse(Options.defaultConfig.deploy_mode)

}
