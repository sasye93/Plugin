package containerize.build

import containerize.IO.Logger
import containerize.types.DockerImage
import containerize.Options

import scala.sys.process.Process

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
      Process(s"docker image prune -f").!(logger)
    }
  }
  def dockerLogin(username : String = Options.dockerUsername, password : String = Options.dockerPassword, host : String = Options.dockerHost) : Unit = {
    if(Process(s"docker login -u $username -p $password $host").!(logger.weak) != 0){
      logger.error(s"Login failed for ${ if(host == "") "DockerHub" else host }, please check you supplied the correct credentials via -P:containerize:username=XXX and -P:containerize:password=XXX")
    }
  }
  def dockerLogout(host : String = Options.dockerHost) : Unit = {
    if(Process(s"docker logout $host").!(logger) != 0){
      logger.warning("Could not logout.")
    }
  }
  def runContainer(tag : DockerImage): Unit ={
    if(Process(s"docker run -d ${tag.tag}").!(logger) != 0){
      logger.warning(s"Error occurred while trying to run container ${tag.tag}, container didn't start.")
    }
  }
}
