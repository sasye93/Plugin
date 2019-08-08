package loci.container

import scala.annotation.meta.{getter, setter}
import scala.collection.immutable.HashMap
import scala.tools.nsc.Global

import containerize.options.Options

@setter @getter
//@compileTimeOnly("this class is for internal use only.")
class ContainerEntryPointImpl[G <: Global](val global : G)(init : ContainerEntryPointImpl[G] = null) {

  import global._

  var _containerEntryClass : Symbol = if(init != null) init._containerEntryClass.asInstanceOf[this.global.Symbol] else NoSymbol
  var _containerPeerClass : Symbol = if(init != null) init._containerPeerClass.asInstanceOf[this.global.Symbol] else NoSymbol
  var _containerEndPoints : collection.mutable.MutableList[ConnectionEndPoint] = if(init != null) init._containerEndPoints.map(_.asInstanceOf[this.ConnectionEndPoint]) else collection.mutable.MutableList()

  case class ConnectionEndPoint(connectionPeer : Symbol,
                                port : Integer = Options.defaultContainerPort,
                                host : String = Options.defaultContainerHost,
                                way : String = "both",  //"listen", "connect" or "both"
                                version : String = Options.defaultContainerVersion)

  def addEndPoint(connectionEndPoint: ConnectionEndPoint) = _containerEndPoints += connectionEndPoint

  def entryClassDefined() : Boolean = _containerEntryClass != NoSymbol
  def peerClassDefined() : Boolean = _containerPeerClass != NoSymbol

  def asSimpleMap() : HashMap[String,String] = {
    HashMap[String, String](
      "_containerEntryClass" -> this._containerEntryClass.fullLocationString,
      "_containerEndPoints" -> this._containerEndPoints.foldLeft("")((s, e) => s.toString + (e.connectionPeer + "|" + e.port + "|" + e.version + "%"))
    )
  }
}