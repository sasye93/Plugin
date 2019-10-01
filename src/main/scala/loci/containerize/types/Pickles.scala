package loci.containerize.types

import java.nio.file.{Path, Paths}
import java.io.File

import loci.containerize.Options
import loci.containerize.Check
import loci.containerize.IO.Logger
import loci.containerize.main.Containerize

import upickle.default.{ReadWriter => RW, _}
import scala.annotation.meta.{getter, setter}
import scala.collection.mutable
import scala.tools.nsc.Global

sealed trait Pickle
object Pickle {
  implicit val rw: RW[Pickle] = RW.merge(
    SimplifiedContainerEntryPoint.rw,
    SimplifiedConnectionEndPoint.rw,
    SimplifiedContainerModule.rw,
    SimplifiedPeerDefinition.rw)
}
//@compileTimeOnly("this class is for internal use only.")

class ContainerEntryPoint(from : SimplifiedContainerEntryPoint, mod : Option[ContainerModule])(implicit val plugin : Containerize) extends SimplifiedContainerEntryPoint(
  from.entryClassSymbolString, from.peerClassSymbolString, from.configPath, from.endPoints, from.isGateway
) {
  import plugin._

  lazy val config : ContainerConfig = new ContainerConfig(configPath, if(mod.isDefined) Some(mod.get.config) else None)(io, plugin)

  @deprecated("1.0") val setupScript = null

  /**
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
      Some(this.containerJSONConfig.getPath),
      containerEndPoints.map{
        c => new SimplifiedConnectionEndPoint(c.connectionPeer.fullName('.'), c.port, c.host, c.way, c.version)
      }.toList
    )
  }
  */
}

case class SimplifiedContainerEntryPoint(
                                          val entryClassSymbolString : String,
                                          val peerClassSymbolString : String,
                                          val configPath : Option[String],
                                          val endPoints : List[SimplifiedConnectionEndPoint] = List[SimplifiedConnectionEndPoint](),
                                          val isGateway : Boolean = false
                                        ) extends Pickle{

  def getLocDenominator : String = Paths.get(loci.container.Tools.getIpString(peerClassSymbolString).split("_").last, loci.container.Tools.getIpString(entryClassSymbolString)).toString
}
object SimplifiedContainerEntryPoint {
  implicit val rw: RW[SimplifiedContainerEntryPoint] = macroRW
}
case class SimplifiedConnectionEndPoint(
                                         val connectionPeerSymbolString : String,
                                         val port : Int,
                                         val host : String,
                                         val way : String,
                                         val version : String,
                                         val method : String = "unknown"
                                       ) extends Pickle
object SimplifiedConnectionEndPoint {
  implicit val rw: RW[SimplifiedConnectionEndPoint] = macroRW
}


class ContainerModule(from : SimplifiedContainerModule)(implicit val plugin : Containerize) extends SimplifiedContainerModule(
  from.moduleName, from.peers, from.configPath
) {
  import plugin._

  lazy val config : ModuleConfig = new ModuleConfig(configPath)(io, plugin)
}
case class SimplifiedContainerModule(
                                      val moduleName : String,
                                      val peers : List[SimplifiedPeerDefinition],
                                      val configPath : Option[String],
                                    ) extends Pickle{

  def getLocDenominator : String = loci.container.Tools.getIpString(moduleName)
}
object SimplifiedContainerModule {
  implicit val rw: RW[SimplifiedContainerModule] = macroRW
}

case class SimplifiedPeerDefinition(
                                     val className : String,
                                   ) extends Pickle
object SimplifiedPeerDefinition {
  implicit val rw: RW[SimplifiedPeerDefinition] = macroRW
}