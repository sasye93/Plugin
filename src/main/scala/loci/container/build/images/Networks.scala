/**
  * Network class, builds network scripts.
  * @author Simon Schönwälder
  * @version 1.0
  */
package loci.container.build.images

import java.io.File
import java.nio.file.{Path,Paths}

import loci.container.build.IO.IO
import loci.container.build.Options
import loci.container.build.main.Containerize

import scala.sys.process.Process

sealed trait INetwork{ def _type : String; }
case object Bridge extends INetwork{ val _type = "bridge"; }
case object Overlay extends INetwork{ val _type = "overlay"; }

class Network(io : IO)(val name : String, buildDir : Path, network : INetwork = Overlay)(implicit plugin : Containerize){

  private var networkDir : File = _

  io.createDir(Paths.get(buildDir.toAbsolutePath.toString, Options.networkDir)) match{
    case Some(f) => networkDir = f
    case None => plugin.logger.error(s"Could not create directory for network scripts inside: $buildDir")
  }
  def getName : String = Options.toolbox.getNameDenominator(name)
  def getType : String = network._type

  def buildSetupScript() : Unit = {
    val CMD =
        s"""docker network inspect ${getName} ${Options.errout}
        |if [ $$? -eq 0 ]; then
        | docker network rm ${getName} ${Options.errout}
        | if [ $$? -ne 0 ]; then
        |   echo "Network '${getName}' already exists, but could not be removed and re-instantiated, probably because it is still in use. If you want to update the network, you must manually remove the old network by first decoupling all connected containers and services from the network ('docker container rm <containerId>', 'docker service rm <serviceId>'), and then 'docker network rm ${getName}'."
        |   exit 1
        | fi
        |fi
        |docker network create --attachable -d ${getType} ${getName}""".stripMargin
    io.buildFile(io.buildScript(CMD), Paths.get(networkDir.getAbsolutePath, getName + ".sh"))
  }
  def buildNetwork() : Unit = {
    Process(s"bash $getName.sh", networkDir).!(plugin.logger)
  }
}
