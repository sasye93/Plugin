package loci.containerize.container

import java.io.File
import java.nio.file.Paths

import scala.sys.process.Process
import loci.containerize.IO.{IO, Logger}
import loci.containerize.{Check, Options}
import loci.containerize.main.Containerize
import java.nio.file.Path

sealed trait INetwork{ def _type : String; }
case object Bridge extends INetwork{ val _type = "bridge"; }
case object Overlay extends INetwork{ val _type = "overlay"; }

class Network(io : IO)(val name : String, buildDir : Path, network : INetwork = Overlay)(implicit plugin : Containerize){

  private var networkDir : File = _

  io.createDir(Paths.get(buildDir.toAbsolutePath.toString, Options.networkDir)) match{
    case Some(f) => networkDir = f
    case None => plugin.logger.error(s"Could not create directory for network scripts inside: $buildDir")
  }
  def getName : String = plugin.toolbox.getNameDenominator(name)
  def getType : String = network._type

  def buildSetupScript() : Unit = {
    val CMD =
        s"""docker network inspect ${getName} > /dev/null 2>&1
        |if [ $$? -eq 0 ]; then
        | docker network rm ${getName} > /dev/null 2>&1
        | if [ $$? -ne 0 ]; then
        |   echo "Network '${getName}' already exists, but could not be removed and re-instantiated, probably because it is still in use. If you want to update the network, you must manually remove the old network by first decoupling all connected containers and services from the network ('docker container rm <containerId>', 'docker service rm <serviceId>'), and then 'docker network rm ${getName}'."
        |   exit 1
        | fi
        |fi
        |docker network create --attachable -d ${getType} ${getName}""".stripMargin
    io.buildFile(io.buildScript(CMD), Paths.get(networkDir.getAbsolutePath, getName + ".sh"))  //todo make all sh, or what
  }
  def buildNetwork() : Unit = {
    Process(s"bash $getName.sh", networkDir).!(plugin.logger)  //todo cmd is win, but not working without...? + cant get err stream because indirect
  }

  /**
  class bridge extends Network{

    def createNetwork(netName : String) : Boolean = Process(s"docker network create $netName").!(logger) == 0
    def joinContainer(netName : String, containerName : String) : Boolean = Process(s"docker network connect $netName $containerName").!(logger) == 0
  }
  class overlay extends Network{

    //todo --attachable means standalone containers can join as well, not only services; --opt encrypted = better sec, lower perf (linux only)
    def createNetwork(netName : String) : Boolean = Process(s"docker network create -d overlay --attachable $netName").!(logger) == 0
    def joinContainer(netName : String, containerName : String) : Boolean = Process(s"docker network connect $netName $containerName").!(logger) == 0
  }
    */
}
