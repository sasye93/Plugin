/**
  * Module config class, config for @containerize.
  * @author Simon Schönwälder
  * @version 1.0
  */
package loci.impl.types

import loci.impl.main.Containerize
import loci.impl.Options
import loci.impl.IO.IO

class ModuleConfig(json : Option[String])(implicit io : IO, implicit private val plugin : Containerize) extends Config(json)(io, plugin) {

  import ModuleConfig.{defaultModuleConfig => default}

  def getAppName : String = Options.toolbox.getNameDenominator(getStringOfKey("app").getOrElse(default.appName))
  def getDisabled : Boolean = getBooleanOfKey("disabled").getOrElse(false)

  def getStateful : Boolean = getBooleanOfKey("stateful").getOrElse(default.stateful)

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
    case Some("mysql") => Some("mysql:latest")
    case Some("none") => None
    case Some(db) => io.logger.warning(s"The option you supplied for globalDb in your module config is not supported and thus discarded: $db"); None //todo custom?
    case None => None
  }
  def getGlobalDbCredentials : Option[(String, String)] = getTupleList("globalDbCredentials", 2).map(t => (t.head.toString, t.last.toString)).headOption.orElse(default.globalDbCredentials)

  def getSecrets : List[(String, String)] = getTupleList("secrets", 2).map(t => (t.head.toString, t.last.toString))
}
object ModuleConfig{
  object defaultModuleConfig{

    val stateful : Boolean =  false

    val appName : String =  Options.swarmName

    val jreBaseImage : String = "jre" //"jre-alpine"
    val globalDb : Option[String] = None //todo: everything else, starting db, persistent /data storage, etc.
    val globalDbCredentials : Option[(String, String)] = None

    val containerVolumeStorage : String = "/data"

    val JSON : String = {
      s"""{
         |  "stateful": $stateful,
         |  "jreBaseImage": "$jreBaseImage",
         |  "containerVolumeStorage": "$containerVolumeStorage"
         |}""".stripMargin
    }//todo "plugin" replace, maybe just with _

  }
}