package loci.containerize.container

import java.io.File
import java.nio.file.Paths

import scala.sys.process.Process
import loci.containerize.IO.{IO, Logger}
import loci.containerize.{Check, Options}
import loci.containerize.main.Containerize
import java.nio.file.Path

sealed trait INetwork{ def _type : String; def name : String }
case object Bridge extends INetwork{ val _type = "bridge"; val name = "mynet"; }
case object Overlay extends INetwork{ val _type = "overlay"; val name = "mynet"; }

class Network[+C <: Containerize](io : IO)(buildDir : Path, network : INetwork = Bridge)(implicit plugin : C){

  private var networkDir : File = _

  io.createDir(Paths.get(buildDir.toAbsolutePath.toString, Options.networkDir)) match{
    case Some(f) => networkDir = f
    case None => plugin.logger.error(s"Could not create directory for network scripts inside: $buildDir")
  }
  def getName : String = network.name
  def getType : String = network._type

  def buildSetupScript() : Unit = {
    val CMD =
      "docker network inspect " + getName + " > /dev/null 2>&1\n" +
        "if [ $? -eq 0 ]; then\n" +
        "\tdocker network rm " + getName + " > /dev/null 2>&1\n" +
        "\tif [ $? -ne 0 ]; then\n" +
        "\t\techo \"Network '" + getName + "' already exists, but could not be removed and re-instantiated. If you want to update the network, you must manually remove the old network by first decoupling all connected containers from the network ('docker container rm <container>'), and then 'docker network rm " + network.name + "'.\"\n" +
        "\t\texit\n" +
        "\tfi\n" +
        "fi\n" +
        s"docker network create -d $getType $getName\n"
    io.buildFile(io.buildScript(CMD), Paths.get(networkDir.getAbsolutePath, s"NetworkSetup.sh"))  //todo make all sh, or what
  }
  def buildNetwork() : Unit = {
    Process("bash " + "NetworkSetup.sh", networkDir).!(plugin.logger)  //todo cmd is win, but not working without...? + cant get err stream because indirect
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
