package containerize.build

import containerize.types.DockerImage
import containerize.options.Options

import scala.sys.process.Process
import scala.tools.nsc.reporters.Reporter

class Runner(reporter: Reporter) {
//todo not working, dockerd seems not enough
  def dockerIsRunning() : Unit = {
    if(Process(s"docker version").! != 0){
      reporter.warning(null, "It seems like the docker deamon is not running, trying to start it...")
      dockerDeamonStartUp()
    }
  }
  def dockerDeamonStartUp() : Unit = {
    //todo see doc
    reporter.info(null, "Starting docker deamon, this can take a while...", false)
    if(Process("dockerd").! != 0){
      reporter.error(null, "Could not start docker deamon. Please start it manually and try again.")
    }
  }

  def dockerLogin(username : String = Options.dockerUsername, password : String = Options.dockerPassword, host : String = Options.dockerHost) : Unit = {
    if(Process(s"docker login -u $username -p $password $host").! != 0){
      reporter.error(null, s"Login failed for ${ host }, please check you supplied the correct credentials via -P:containerize:username:XXX and -P:containerize:password:XXX")
    }
  }
  def dockerLogout(host : String = Options.dockerHost) : Unit = {
    if(Process(s"docker logout $host").! != 0){
      reporter.warning(null, "Could not logout.")
    }
  }
  def runContainer(tag : DockerImage): Unit ={
    if(Process(s"docker run -d ${tag.tag}").! != 0){
      reporter.warning(null, s"Error occurred while trying to run container ${tag.tag}, container didn't start.")
    }
  }
}
