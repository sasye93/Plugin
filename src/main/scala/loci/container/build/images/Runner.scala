/**
  * Runner class, runs created scripts.
  * @author Simon Schönwälder
  * @version 1.0
  */
package loci.container.build.images

import java.nio.file.Path

import loci.container.build.IO.Logger
import loci.container.build.Options

import scala.sys.process._

/**
  * run cmd:
  * docker run -a -i -p 43055:43055 --cap-add=NET_ADMIN --cap-add=NET_RAW --sysctl net.ipv4.conf.eth0.route_localnet=1 -t XXXIMAGE -name XXXIMAGE
  */

class Runner(logger : Logger) {
  /**
    * Check if docker is running and we have bash support, these are prerequisites.
   */
  def dockerIsRunning() : Boolean = Process("docker version").!(logger.weak) == 0
  def requirementsCheck() : Boolean = {
    (Process("bash -lc \"echo 0\"").!(logger.weak) == 0) || {//orig: -> cmd /c bash -lc "echo 0"
      Process("bash -lc \"echo 0\"").!(logger)
      logger.error("Couldn't find bash command. This is probably due to missing bash shell support. Make sure you have a bash interpreter (e.g. cygwin64) installed.")
      false
    }
  }
  /**
    * Check if docker is running, this is a prerequisite.
    */
  def dockerRun() : Boolean = {
    if(dockerIsRunning()) {
      logger.info(s"Docker running, version ${Process("docker version --format '{{.Server.Version}}'").!!}")
      true
    }
    else{
      logger.warning("It seems like the docker daemon is not running. Do you have Docker installed and up? Please start it manually and try again. Containerization Extension is disabled.")
      false
    }
  }
  //todo check if this goes to far, maybe -filter it
  /**
    * Cleanup dangling Docker stuff if option is set.
    */
  def dockerCleanup() : Unit = {
    if(Options.cleanup){
      (Process(s"docker image prune -f") #&&
        Process("docker volume prune -f") #&&
        Process("docker network prune -f") #&&
        Process("docker container prune -f")).!(logger)
    }
  }
  /**
    * Login to the Docker registry host (default DockerHub).
    */
  def dockerLogin(username : String = Options.dockerUsername, password : String = Options.dockerPassword, host : String = Options.dockerHost) : Unit = {
    if((Process("echo " + password + "") #| Process(s"docker login --username $username --password-stdin $host")).!(logger.weak) != 0){// -> orig: "cmd /c echo " + password + ""
      logger.error(s"Login failed for ${ if(host == "") "DockerHub" else host }, please check you supplied the correct credentials via the dockerUsername (${ Options.dockerUsername }) and dockerPassword compiler options.")
    }
  }
  /**
    * Logout of the Docker registry host (default DockerHub).
    */
  def dockerLogout(host : String = Options.dockerHost) : Unit = {
    if(Process(s"docker logout $host").!(logger) != 0){
      logger.warning("Could not logout.")
    }
  }
  /**
    * Pre-pull an image from the registry.
    */
  def dockerPull(tag : String) : Unit = {
    Process(s"docker pull $tag").!(logger)
  }
}
