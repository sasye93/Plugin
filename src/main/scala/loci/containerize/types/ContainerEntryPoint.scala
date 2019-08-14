package loci.containerize.types

import java.nio.file.Path
import java.io.File

import loci.containerize.Options
import loci.containerize.main.Containerize

import scala.annotation.meta.{getter, setter}
import scala.collection.mutable

import scala.tools.nsc.Global

@setter @getter
//@compileTimeOnly("this class is for internal use only.")
class ContainerEntryPoint[+C <: Containerize](init : ContainerEntryPoint[C] = null)(implicit val parent : C) {

  val global : Global = parent.global

  import parent._
  import global._

  var containerEntryClass : Symbol = if(init != null) init.containerEntryClass.asInstanceOf[this.global.Symbol] else NoSymbol
  var containerPeerClass : Symbol = if(init != null) init.containerPeerClass.asInstanceOf[this.global.Symbol] else NoSymbol
  var containerEndPoints : mutable.MutableList[ConnectionEndPoint] = if(init != null) init.containerEndPoints.map(_.asInstanceOf[this.ConnectionEndPoint]) else mutable.MutableList()

  var containerJSONConfig : File = _

  case class ConnectionEndPoint(connectionPeer : Symbol,
                                port : Integer = Options.defaultContainerPort,
                                host : String = Options.defaultContainerHost,
                                way : String = "both",  //"listen", "connect" or "both"
                                version : String = Options.defaultContainerVersion)

  def addEndPoint(connectionEndPoint: ConnectionEndPoint) : mutable.MutableList[ConnectionEndPoint] = containerEndPoints += connectionEndPoint
  def getDefaultEndpoint(connectionPeer : Symbol) : ConnectionEndPoint = ConnectionEndPoint(connectionPeer)

  def getLocDenominator : String = containerEntryClass.fullNameString.replace("$", "_") + "_" + containerPeerClass.fullNameString.replace("$", "_")

  def entryClassDefined() : Boolean = containerEntryClass != NoSymbol
  def peerClassDefined() : Boolean = containerPeerClass != NoSymbol
  def configDefined() : Boolean = containerJSONConfig != null

  def setConfig(path : Path) : Unit = {
    val f : File = path.toFile
    if(f.exists() && f.isFile)
      this.containerJSONConfig = f
    else
      parent.logger.error(s"Cannot find XML config file for entry point: ${path.toString}")

  }

  def asSimplifiedEntryPoints() : SimplifiedContainerEntryPoint = {
    SimplifiedContainerEntryPoint(this.containerEntryClass.fullName('.'), this.containerPeerClass.fullName('.'), containerEndPoints.map{
      c => SimplifiedConnectionEndPoint(c.connectionPeer.fullName('.'), c.port, c.host, c.way, c.version)
    }.toList)
  }
}
case class SimplifiedContainerEntryPoint(entryClassSymbolString : String, peerClassSymbolString : String, endPoints : List[SimplifiedConnectionEndPoint])
case class SimplifiedConnectionEndPoint(connectionPeerSymbolString : String, port : Integer, host : String, way : String, version : String)