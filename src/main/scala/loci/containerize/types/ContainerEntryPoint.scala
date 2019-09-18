package loci.containerize.types

import java.nio.file.Path
import java.io.File

import loci.containerize.Options
import loci.containerize.Check
import loci.containerize.main.Containerize

import scala.annotation.meta.{getter, setter}
import scala.collection.mutable

import scala.tools.nsc.Global

//@compileTimeOnly("this class is for internal use only.")
class ContainerEntryPoint(init : ContainerEntryPoint = null)(implicit val plugin : Containerize) {

  import plugin._
  import plugin.global._

  var containerPeerClass : Symbol = if(init != null) init.containerPeerClass.asInstanceOf[global.Symbol] else NoSymbol
  var containerEntryClass : Symbol = if(init != null) init.containerEntryClass.asInstanceOf[global.Symbol] else NoSymbol

  var containerEndPoints : mutable.MutableList[ConnectionEndPoint] = if(init != null) init.containerEndPoints.map(_.asInstanceOf[this.ConnectionEndPoint]) else mutable.MutableList()

  var containerJSONConfig : File = _
  var containerSetupScript : File = _

  case class ConnectionEndPoint(connectionPeer : Symbol,
                                port : Integer = Options.defaultContainerPort,
                                host : String = Options.defaultContainerHost,
                                way : String = "both",  //"listen", "connect" or "both"
                                version : String = Options.defaultContainerVersion)

  def addEndPoint(connectionEndPoint: ConnectionEndPoint) : mutable.MutableList[ConnectionEndPoint] = containerEndPoints += connectionEndPoint
  def getDefaultEndpoint(connectionPeer : Symbol) : ConnectionEndPoint = ConnectionEndPoint(connectionPeer)

  def getLocDenominator : String = plugin.toolbox.getNormalizedNameDenominator(containerPeerClass) + "/" + plugin.toolbox.getNormalizedNameDenominator(containerEntryClass)

  def entryClassDefined() : Boolean = containerEntryClass != NoSymbol
  def peerClassDefined() : Boolean = containerPeerClass != NoSymbol
  def configDefined() : Boolean = containerJSONConfig != null
  def setupScriptDefined() : Boolean = containerSetupScript != null

  def setConfig(path : Path) : Unit = {
    logger.info(path.toString)
    val f : File = Options.resolvePath(path)(logger).orNull
    if(Check ? f && f.exists() && f.isFile)
      this.containerJSONConfig = f
    else
      logger.error(s"Cannot find annotated JSON config file for entry point: ${path.toString}")
  }
  def setScript(path : Path) : Unit = {
    logger.info(path.toString)
    val f : File = Options.resolvePath(path)(logger).orNull
    if(Check ? f && f.exists() && f.isFile)
      this.containerSetupScript = f
    else
      logger.error(s"Cannot find annotated script file for entry point: ${path.toString}")
  }

  def asSimplifiedEntryPoints() : SimplifiedContainerEntryPoint = {
    new SimplifiedContainerEntryPoint(
      this.containerEntryClass.fullName('.'),
      this.containerPeerClass.fullName('.'),
      this.containerJSONConfig,
      this.containerSetupScript,
      containerEndPoints.map{
        c => new SimplifiedConnectionEndPoint(c.connectionPeer.fullName('.'), c.port, c.host, c.way, c.version)
      }.toList
    )
  }
}
@SerialVersionUID(123L)
final class SimplifiedContainerEntryPoint(
                                          val entryClassSymbolString : String,
                                          val peerClassSymbolString : String,
                                          val config : File,
                                          val setupScript : File,
                                          val endPoints : List[SimplifiedConnectionEndPoint],
                                          val isGateway : Boolean = false
                                        ) extends Serializable
@SerialVersionUID(124L)
final class SimplifiedConnectionEndPoint(
                                         val connectionPeerSymbolString : String,
                                         val port : Integer,
                                         val host : String,
                                         val way : String,
                                         val version : String,
                                         val method : String = "unknown"
                                       ) extends Serializable
@SerialVersionUID(125L)
final class SimplifiedContainerModule(
                                     val moduleName : String,
                                     val peers : List[String]
                                     ) extends Serializable