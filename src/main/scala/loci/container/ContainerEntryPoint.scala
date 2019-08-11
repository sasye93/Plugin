package loci.container

import scala.annotation.meta.{getter, setter}
import scala.collection.immutable.HashMap
import scala.collection.mutable
import scala.tools.nsc.Global

import containerize.Options

@setter @getter
//@compileTimeOnly("this class is for internal use only.")
class ContainerEntryPoint[G <: Global](val global : G)(init : ContainerEntryPoint[G] = null) {

  import global._

  var containerEntryClass : Symbol = if(init != null) init.containerEntryClass.asInstanceOf[this.global.Symbol] else NoSymbol
  var containerPeerClass : Symbol = if(init != null) init.containerPeerClass.asInstanceOf[this.global.Symbol] else NoSymbol
  var containerEndPoints : mutable.MutableList[ConnectionEndPoint] = if(init != null) init.containerEndPoints.map(_.asInstanceOf[this.ConnectionEndPoint]) else mutable.MutableList()

  case class ConnectionEndPoint(connectionPeer : Symbol,
                                port : Integer = Options.defaultContainerPort,
                                host : String = Options.defaultContainerHost,
                                way : String = "both",  //"listen", "connect" or "both"
                                version : String = Options.defaultContainerVersion)

  def addEndPoint(connectionEndPoint: ConnectionEndPoint) : mutable.MutableList[ConnectionEndPoint] = containerEndPoints += connectionEndPoint
  def getDefaultEndpoint(connectionPeer : Symbol) : ConnectionEndPoint = ConnectionEndPoint(connectionPeer)

  def entryClassDefined() : Boolean = containerEntryClass != NoSymbol
  def peerClassDefined() : Boolean = containerPeerClass != NoSymbol

  def asSimplifiedEntryPoints() : SimplifiedContainerEntryPoint = {
    SimplifiedContainerEntryPoint(this.containerEntryClass.fullName('.'), this.containerPeerClass.fullName('.'), containerEndPoints.map{
      c => SimplifiedConnectionEndPoint(c.connectionPeer.fullName('.'), c.port, c.host, c.way, c.version)
    }.toList)
  }
}
case class SimplifiedContainerEntryPoint(entryClassSymbolString : String, peerClassSymbolString : String, endPoints : List[SimplifiedConnectionEndPoint])
case class SimplifiedConnectionEndPoint(connectionPeerSymbolString : String, port : Integer, host : String, way : String, version : String)