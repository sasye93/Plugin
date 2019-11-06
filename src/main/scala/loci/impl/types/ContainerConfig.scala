/**
  * Container config class, optional config for @service/@gateway.
  * @author Simon Schönwälder
  * @version 1.0
  */
package loci.impl.types

import loci.impl.IO.IO
import loci.impl.Options
import loci.impl.main.Containerize

class ContainerConfig(json : Option[String], moduleConfig : Option[ModuleConfig])(implicit io : IO, implicit private val plugin : Containerize) extends Config(json, {
  if(moduleConfig.isDefined) moduleConfig.get.getHome else None
})(io, plugin) {

  import ContainerConfig.{defaultContainerConfig => default}

  //Docker/Swarm-specific
  def getReplicas : Integer = if(moduleConfig.isDefined && moduleConfig.get.getStateful) 1 else Math.ceil(getDoubleOfKey("replicas").getOrElse(default.replicas)).toInt
  def getCPULimit : Double = getDoubleOfKey("cpu_limit").getOrElse(default.cpu_limit)
  def getCPUReserve : Double = getDoubleOfKey("cpu_reserve").getOrElse(default.cpu_reserve)
  def getMemLimit : String = getStringOfKey("memory_limit").getOrElse(default.memory_limit)
  def getMemReserve : String = getStringOfKey("memory_reserve").getOrElse(default.memory_reserve)
  def getDeployMode : String = getStringOfKey("deploy_mode").getOrElse(default.deploy_mode)
  def getAttachable : Boolean = getBooleanOfKey("attachable").getOrElse(default.attachable)

  //Non-Docker specific
  def getNetworkMode : String = getStringOfKey("network_mode").getOrElse(default.network_mode)
  def getDescription : String = StringContext.processEscapes(getStringOfKey("description").getOrElse(default.description).trim)

  //Local database
  def getLocalDbIdentifier : Option[String] = getStringOfKey("localDb").orElse(default.localDb)

  //Note that if you add new db here, Tools.localDb and Tools.globalDb must be altered.
  def getLocalDb : Option[String] = getLocalDbIdentifier match{
    case Some("mongo") => Some("mongo:latest")
    case Some("none") => None
    case Some(db) => io.logger.warning(s"The option you supplied for localDb in your module config is not supported and thus discarded: $db"); None
    case None => None
  }
  def getLocalDbCredentials : Option[(String, String)] = getTupleList("localDbCredentials", 2).map(t => (t.head.toString, t.last.toString)).headOption.orElse(default.localDbCredentials)
  def getSecrets : Set[String] = getListOfKey("secrets").toSet
  def getPorts : List[Int] = getListOfKey("ports").map(_.toInt).filter(p => p >= 0 && p <= 65535)
  def getEndpointMetadata : List[(String, String, String, Int)] = getTupleList("endpoints", 4).map(t => scala.util.Try{ (t.head.toString, t(2).toString, t(3).toString, Integer.parseInt(String.valueOf(t.last))) }.getOrElse(null)).filter(_ != null)

  def getServiceMetadata(d : TempLocation) : String = {
    //todo implement
    val api = d.entryPoint
    val service = d.getImageName
    def getConnectionDescriptions(filterNot : String) : String = {
      val cons = d.entryPoint.endPoints.filter(_.way != filterNot).map(e => Tuple4(e.way, e.method, e.connectionPeerSymbolString, e.port)) ++ d.entryPoint.config.getEndpointMetadata.filter(_._1 != filterNot)
      if(cons.isEmpty) " -\n" else cons.foldLeft("")((E, ep) => E + s"@${ep._2}\t:${ep._4}\t\t[ ${ep._3} ]" + "\n")
    }
    val descr = s"""
                   |Description for service: $service
                   |----------------
                   |  SERVICE API
                   |----------------
                   |$service provides the following to services:\n""" +
      getConnectionDescriptions("connect") +
      s"""
                   |$service requires the following services:\n""" +
      getConnectionDescriptions("listen") +
      s"""
         |""".stripMargin
    Options.labelPrefix + ".api: \"" + descr + "\""
  }
}
object ContainerConfig{
  object defaultContainerConfig{
    val replicas : Double = 1
    val cpu_limit : Double = 0.2
    val cpu_reserve : Double = 0.1
    val memory_limit : String = "128M"
    val memory_reserve : String = "64M"
    val deploy_mode : String = "replicated"
    val attachable : Boolean = true

    // non docker specific options
    val network_mode : String = "default"  //default | isolated  //todo this is now only for single services (prod isolation), also make on module level and prohibit this for global config.

    val description : String = "No description available for this service."

    // non docker specific without default
    var localDb : Option[String] = None //todo: everything else, starting db, persistent /data storage, etc.
    var localDbCredentials : Option[(String, String)] = None

    val JSON : String = {
      "{" +
        "\"attachable\":\"" + attachable + "\"," +
        "\"deploy_mode\":\"" + deploy_mode + "\"," +
        "\"replicas\":\"" + replicas + "\"," +
        "\"cpu_limit\":\"" + cpu_limit + "\"," +
        "\"cpu_reserve\":\"" + cpu_reserve + "\"," +
        "\"memory_limit\":\"" + memory_limit + "\"," +
        "\"memory_reserve\":\"" + memory_reserve + "\"," +
        "\"network_mode\":\"" + network_mode + "\"," + // non docker specific options
        "\"description\":\"" + description + "\"" + // non docker specific options
        "}"
    }
  }
}