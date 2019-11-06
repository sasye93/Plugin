/**
  * Pickling (serialization) classes. They are used to store information by the macros, which are later re-loaded by the build stage.
  * (information passing between unconnected stages of the extension).
  */
package loci.impl.types

import java.nio.file.Paths
import loci.impl.main.Containerize

import upickle.default.{ReadWriter => RW, _}

sealed trait Pickle
object Pickle {
  implicit val rw: RW[Pickle] = RW.merge(
    SimplifiedContainerEntryPoint.rw,
    SimplifiedConnectionEndPoint.rw,
    SimplifiedContainerModule.rw,
    SimplifiedPeerDefinition.rw)
}

class ContainerEntryPoint(from : SimplifiedContainerEntryPoint, mod : Option[ContainerModule])(implicit val plugin : Containerize) extends SimplifiedContainerEntryPoint(
  from.entryClassSymbolString, from.peerClassSymbolString, from.configPath, from.endPoints, from.isGateway
) {
  import plugin._

  lazy val config : ContainerConfig = new ContainerConfig(configPath, if(mod.isDefined) Some(mod.get.config) else None)(io, plugin)
}

case class SimplifiedContainerEntryPoint(
                                          entryClassSymbolString : String,
                                          peerClassSymbolString : String,
                                          configPath : Option[String],
                                          endPoints : List[SimplifiedConnectionEndPoint] = List[SimplifiedConnectionEndPoint](),
                                          isGateway : Boolean = false
                                        ) extends Pickle{

  def getLocDenominator : String = Paths.get(loci.container.Tools.getIpString(peerClassSymbolString).split("_").last, loci.container.Tools.getIpString(entryClassSymbolString)).toString
}
object SimplifiedContainerEntryPoint {
  implicit val rw: RW[SimplifiedContainerEntryPoint] = macroRW
}
case class SimplifiedConnectionEndPoint(
                                         connectionPeerSymbolString : String,
                                         port : Int,
                                         host : String,
                                         way : String,
                                         version : String,
                                         method : String = "unknown"
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
                                      moduleName : String,
                                      peers : List[SimplifiedPeerDefinition],
                                      configPath : Option[String],
                                    ) extends Pickle{

  def getLocDenominator : String = loci.container.Tools.getIpString(moduleName)
}
object SimplifiedContainerModule {
  implicit val rw: RW[SimplifiedContainerModule] = macroRW
}

case class SimplifiedPeerDefinition(
                                     className : String,
                                   ) extends Pickle
object SimplifiedPeerDefinition {
  implicit val rw: RW[SimplifiedPeerDefinition] = macroRW
}