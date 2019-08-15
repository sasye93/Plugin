package loci.containerize.container

import java.nio.file.{Path, Paths}

import loci.containerize.IO.Logger
import loci.containerize.Options
import loci.containerize.types.TempLocation

import scala.sys.process._


/**
  * run cmd:
  * docker run -a -i -p 43055:43055 --cap-add=NET_ADMIN --cap-add=NET_RAW --sysctl net.ipv4.conf.eth0.route_localnet=1 -t XXXIMAGE -name XXXIMAGE
  */

class Runner(logger : Logger) {
//todo not working, dockerd seems not enough
  def dockerIsRunning() : Boolean = Process(s"docker version").!(logger) == 0
  def dockerRun() : Boolean = {
    if(dockerIsRunning()) {
      logger.info(s"Docker running, version ${Process("docker version --format '{{.Server.Version}}'").!!}")
      true
    }
    else{
      logger.warning("It seems like the docker deamon is not running, trying to start it...")
      dockerDeamonStartUp()
    }
  }
  def dockerDeamonStartUp() : Boolean = {
    //todo see doc
    logger.info("Starting docker deamon, this can take a while...")
    if(Process("dockerd").!(logger) == 0) {
      true
    }
    else{
      logger.error("Could not start docker deamon. Please start it manually and try again.")
      false
    }
  }
  //check if this goes to far, maybe -filter it
  def dockerCleanup() : Unit = {
    if(Options.cleanup){
      (Process(s"docker image prune -f") #&&
        Process("docker volume prune -f") #&&
        Process("docker network prune -f") #&&
        Process("docker container prune -f")).!(logger)
    }
  }
  def dockerLogin(username : String = Options.dockerUsername, password : String = Options.dockerPassword, host : String = Options.dockerHost) : Unit = {
    if(Process(s"docker login -u $username -p $password $host").!(logger.weak) != 0){
      logger.error(s"Login failed for ${ if(host == "") "DockerHub" else host }, please check you supplied the correct credentials via -P:loci.containerize:username=XXX and -P:loci.containerize:password=XXX")
    }
  }
  def dockerLogout(host : String = Options.dockerHost) : Unit = {
    if(Process(s"docker logout $host").!(logger) != 0){
      logger.warning("Could not logout.")
    }
  }

  //todo doesnt care about order
  @deprecated("")
  def runLandscape(dirs : List[TempLocation]) : Unit = {
    dirs.foreach{ d => //todo this is windows only
      Process("cmd /k start \"t\" /W /NORMAL /SEPARATE cmd /k RunContainer.$osExt", d.tempPath.toFile)//todo logger, .sh NOT WORKINMG: network not working.
    }
  }

  object Swarm{
    def init() : Unit = {
      if(Process(s"docker swarm init").!(logger) != 0){
        logger.error(s"Error when trying to initialize Docker Swarm.")
      }
    }
    def deploy(appName : String, composeFile : Path) : Unit = {
      if(Process("docker stack deploy -c \"" + composeFile.toAbsolutePath.toString + "\" " + appName).!(logger) != 0){
        logger.error(s"Error when trying to deploy swarm stack.")
      }
    }
  }

}
