/**
  * Config class, the common superclass of container {@link loci.impl.types.ContainerConfig} and module {@link loci.impl.types.ModuleConfig} configs.
  * @author Simon Schönwälder
  * @version 1.0
  */
package loci.impl.types

import java.io.File

import scala.util.Try
import scala.util.parsing.json.JSON
import loci.impl.main.Containerize
import loci.impl.{Check,Options}
import loci.impl.IO._

abstract class Config(json : Option[String], homeDir : Option[String] = None)(implicit io : IO, implicit private val plugin : Containerize) {

  //todo check and document if this works
  protected def loadConfig() : String = {
    val configFile : Option[File] = json match{
      case Some(cfg) =>
        val f : File = io.resolvePath(cfg, getHome.orNull)(io.logger).orNull
        if(Check ? f && f.exists() && f.isFile)
          Some(f)
        else None
      case None => None
    }

    if (json.isDefined){
      if(configFile.isDefined) io.readFromFile(configFile.get.toPath)
      else StringContext.processEscapes(json.get).stripMargin
    }
    else {
      ContainerConfig.defaultContainerConfig.JSON
    }
  }
  protected val config : String = loadConfig()
  protected val parsed: Option[Any] = JSON.parseFull(config)

  protected val map: Map[String, Any] = parsed match {
    case Some(e: Map[String, Any]) => e
    case _ => plugin.logger.error(s"Parsing JSON config for entry point failed, ${if (Check ? config) ": " + config.toString + ". Not a valid JSON document?" else "default options seem to be broke (internal failure). Please supply a manual config file with @config."}"); null
  }

  {
    val cfgClass = this.getClass.getName
    val cfgType = this.getConfigType
    if(cfgType.isDefined){
      if(cfgType.get == "module" && (cfgClass != Options.toolbox.toInstanceClassName(ModuleConfig))) io.logger.error("You cannot pass a module config as a service config (type=module).")
      if(cfgType.get == "service" && (cfgClass != Options.toolbox.toInstanceClassName(ContainerConfig))) io.logger.error("You cannot pass a service config as a module config (type=service).")
    }
  }

  protected def getValueOfKey(key : String) : Option[Any] = Check ?=>[Option[Any]] (this.map, this.map.get(key), None)
  protected def parseString(v : Any) : Option[String] = Try { v.toString }.toOption
  protected def parseDouble(v : Any) : Option[Double] = Try { parseString(v).get.toDouble }.toOption
  protected def parseBoolean(v : Any) : Option[Boolean] = Try { parseString(v).get.toBoolean }.toOption
  protected def parseList(v : Any) : Option[List[String]] = Try { parseString(v).get.split(',').toList }.toOption
  protected def parseTupleList(v : Any) : Option[List[List[String]]] = Try { parseString(v).get.split('|').map(_.stripPrefix("(").stripSuffix(")").trim).map(_.split(',').toList).toList }.toOption

  protected def getTupleList(key : String, length : Int) : List[List[Any]] = getValueOfKey(key) match{ case Some(s) => parseTupleList(s).getOrElse(List()).filter(_.length == length) case _ => List() }
  protected def getListOfKey(key : String) : List[String] = getValueOfKey(key) match{ case Some(s) => parseList(s).getOrElse(List[String]()); case _ => List[String]() }
  protected def getStringOfKey(key : String) : Option[String] = getValueOfKey(key) match{ case Some(s) => parseString(s); case _ => None }
  protected def getDoubleOfKey(key : String) : Option[Double] = getValueOfKey(key) match{ case Some(s) => parseDouble(s); case _ => None }
  protected def getBooleanOfKey(key : String) : Option[Boolean] = getValueOfKey(key) match{ case Some(s) => parseBoolean(s); case _ => None }

  private def getConfigType : Option[String] = getStringOfKey("type")
  def getHome : Option[String] = homeDir.orElse(getStringOfKey("home"))

  def getScript(implicit logger : Logger) : Option[File] = {
    getStringOfKey("script") match{
      case Some(script) =>
        var f : File = io.resolvePath(script, getHome.orNull).orNull
        if(Check ? f && f.exists() && f.isFile) {
          //todo make check if \r, but doesnt work, all of this doesnt work.
          f = io.buildFile(io.readFromFile(f), f.toPath, true, true).getOrElse(f)
          //logger.warning("The script you provided apparently does not have UNIX endings, which could prevent proper execution. It has been transformed automatically and might run properly, but you should manually check your script not to use \"\\r\" endings: " + f.getPath)
          Some(f)
        } else {
          logger.error(s"Cannot find annotated JSON config file for entry point: $script")
          None
        }
      case None => None
    }
  }
  def getCustomBaseImage : Option[String] = getStringOfKey("customBaseImage")
  var customBaseImage : Option[String] = None

  /**
   * todo: keep this global version? is referenced auf jeden fall in config below
   * recommended:
   * => jre-alpine
   * => redis (smallest) or couchdb
   */
}