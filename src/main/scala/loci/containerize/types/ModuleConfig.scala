package loci.containerize.types

import java.io.File
import java.nio.file.{Files, Path, Paths}

import scala.util.Try
import scala.util.parsing.json.JSON
import loci.containerize.main.Containerize
import loci.containerize.Options
import loci.containerize.Check
import loci.containerize.IO.IO

class ModuleConfig(json : Option[String])(implicit io : IO, implicit private val plugin : Containerize) extends Config(json)(io, plugin) {

  import ModuleConfig.{defaultModuleConfig => default}

  def getCleanBuilds : Boolean = getBooleanOfKey("cleanBuilds").getOrElse(default.cleanBuilds)
  def getCleanups : Boolean = getBooleanOfKey("cleanup").getOrElse(default.cleanup)
  def getNoCache : Boolean = getBooleanOfKey("nocache").getOrElse(default.nocache)
  def getSaveImages : Boolean = getBooleanOfKey("saveImages").getOrElse(default.saveImages)
  def getShowInfos : Boolean = getBooleanOfKey("showInfos").getOrElse(default.showInfos)

  def getDockerRepository : String = getStringOfKey("dockerRepository").getOrElse(default.dockerRepository)
  def getDockerHost : Option[String] = getStringOfKey("dockerHost").orElse(default.dockerHost)

  def getContainerVolumeStorage : String = getStringOfKey("containerVolumeStorage").getOrElse(default.containerVolumeStorage)

  def getJreBaseImage : String = getStringOfKey("jreBaseImage").getOrElse(default.jreBaseImage) match{
    case "jre" => "openjdk:8-jre"
    case "jre-latest" => "openjdk:jre"
    case "jre-small" => "openjdk:8-jre-small"
    case "jre-small-latest" => "openjdk:jre-small"
    case "jre-alpine" => "openjdk:8-jre-alpine"
    case "jre-alpine-latest" => "openjdk:jre-alpine"
    case other => other //todo make this customizable, if not matching one of these direct inject
  }
  //Docker/Swarm-specific
  def getGlobalDbIdentifier : Option[String] = getStringOfKey("globalDb").orElse(default.globalDb)
  def getGlobalDb : Option[String] = getGlobalDbIdentifier match{
    case Some("mongo") => Some("mongo:latest")
    case Some("none") => None
    case Some(db) => io.logger.warning(s"The option you supplied for globalDb in your module config is not supported and thus discarded: $db"); None //todo custom?
    case None => None
  }
  def getGlobalDbCredentials : Option[(String, String)] = getTupleList("globalDbCredentials", 2).map(t => (t.head.toString, t.last.toString)).headOption.orElse(default.globalDbCredentials)

  def getSecrets : List[(String, String)] = getTupleList("secrets", 2).map(t => (t.head.toString, t.last.toString))
}
object ModuleConfig{
  object defaultModuleConfig{
    val cleanBuilds : Boolean = true
    val cleanup : Boolean =  true
    val nocache : Boolean =  false
    val saveImages : Boolean =  false
    val showInfos : Boolean =  true

    val dockerRepository : String = "plugin"
    val dockerHost : Option[String] = None

    val jreBaseImage : String = "jre" //"jre-alpine"
    val globalDb : Option[String] = None //todo: everything else, starting db, persistent /data storage, etc.
    val globalDbCredentials : Option[(String, String)] = None

    val containerVolumeStorage : String = "/data"

    val JSON : String = {
      s"""{
         |  "cleanBuilds": $cleanBuilds,
         |  "cleanup": $cleanup,
         |  "nocache": $nocache,
         |  "saveImages": $saveImages,
         |  "showInfos": $showInfos,
         |  "dockerRepository": "$dockerRepository",
         |  "jreBaseImage": "$jreBaseImage",
         |  "containerVolumeStorage": "$containerVolumeStorage"
         |}""".stripMargin
    }//todo "plugin" replace, maybe just with _
    
/**
    var dockerUsername : String = "sasye93"
    var dockerPassword : String = "Jana101997"
*/
  }
}