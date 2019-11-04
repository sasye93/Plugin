/**
  * Runner class, runs created scripts.
  * @author Simon Schönwälder
  * @version 1.0
  */
package loci.impl.container

import java.nio.file.Path

import loci.impl.IO.Logger
import loci.impl.Options
import loci.impl.types.TempLocation

import scala.sys.process._


/**
  * run cmd:
  * docker run -a -i -p 43055:43055 --cap-add=NET_ADMIN --cap-add=NET_RAW --sysctl net.ipv4.conf.eth0.route_localnet=1 -t XXXIMAGE -name XXXIMAGE
  */

class Runner(logger : Logger) {
//todo not working, dockerd seems not enough
  def dockerIsRunning() : Boolean = (Process("docker version").!(logger.weak) == 0)
  def requirementsCheck() : Boolean = {
    (Process("cmd /c bash -lc \"echo 0\"").!(logger.weak) == 0) || {
      (Process("cmd /c bash -lc \"echo 0\"").!(logger))
      logger.error("Couldn't find bash command. This is probably due to missing bash shell support. Make sure you have a bash interpreter (e.g. cygwin64) installed.")
      false
    }
  }
  def dockerRun() : Boolean = {
    if(dockerIsRunning()) {
      logger.info(s"Docker running, version ${Process("docker version --format '{{.Server.Version}}'").!!}")
      true
    }
    else{
      logger.warning("It seems like the docker daemon is not running, trying to start it...")
      dockerDeamonStartUp()
    }
  }
  def dockerDeamonStartUp() : Boolean = {
    //todo see doc
    logger.info("Starting docker daemon, this can take a while...")
    Process("dockerd").! == 0 || {
      logger.warning("Could not start docker daemon (dockerd). Do you have Docker installed? Please start it manually and try again. Containerization Extension is disabled.")
      false
    }
  }
  //todo check if this goes to far, maybe -filter it
  def dockerCleanup() : Unit = {
    if(Options.cleanup){
      (Process(s"docker image prune -f") #&&
        Process("docker volume prune -f") #&&
        Process("docker network prune -f") #&&
        Process("docker container prune -f")).!(logger)
    }
  }
  def dockerLogin(username : String = Options.dockerUsername, password : String = Options.dockerPassword, host : String = Options.dockerHost) : Unit = {
    if((Process("cmd /c echo " + password + "") #| Process(s"docker login --username $username --password-stdin $host")).!(logger.weak) != 0){
      logger.error(s"Login failed for ${ if(host == "") "DockerHub" else host }, please check you supplied the correct credentials via the dockerUsername (${ Options.dockerUsername }) and dockerPassword compiler options.")
    }
  }
  def dockerLogout(host : String = Options.dockerHost) : Unit = {
    if(Process(s"docker logout $host").!(logger) != 0){
      logger.warning("Could not logout.")
    }
  }
  def dockerPull(tag : String) : Unit = {
    Process(s"docker pull $tag").!(logger)
  }

  //todo doesnt care about order
  @deprecated("")
  def runLandscape(dirs : List[TempLocation]) : Unit = {
    dirs.foreach{ d => //todo this is windows only
      Process("cmd /k start \"t\" /W /NORMAL /SEPARATE cmd /k RunContainer.$osExt", d.getTempFile)//todo logger, .sh NOT WORKINMG: network not working.
    }
  }

  object Swarm{
    def init() : Unit = {
      if(Process(s"docker swarm init").!(logger) != 0){
        logger.error(s"Error while trying to initialize Docker Swarm.")
      }
    }
    def deploy(appName : String, composeFile : Path) : Unit = {
      if(Process("docker stack deploy -c \"" + composeFile.toAbsolutePath.toString + "\" " + appName).!(logger) != 0){
        logger.error(s"Error while trying to deploy swarm stack.")
      }
    }
  }

}
