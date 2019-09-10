package loci.containerize.container

import loci.containerize.IO.IO
import loci.containerize.Options
import loci.containerize.main.Containerize
import loci.containerize.types.TempLocation

import sys.process._

class Manager(implicit plugin : Containerize){

  def serviceExists(d : TempLocation) : Boolean = {
    Process(s"docker service inspect ${ d.getImageName }").! == 0
  }

  //todo this is a stub
  def updateService(d : TempLocation) : Unit = {
    if(serviceExists(d))
      Process(s"docker service update --image ${ Options.dockerUsername }/${ Options.dockerRepository.toLowerCase }:${ d.getImageName }").!(plugin.logger)
  }
}
