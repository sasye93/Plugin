package loci.containerize.types

import java.io.File
import java.nio.file.{Path, Paths}

import loci.containerize.Options

import scala.annotation.meta.{getter, setter}

case class TempLocation(classSymbol : String, private val tempPath : Path, entryPoint : ContainerEntryPoint){
  def getImageName : String = loci.container.Tools.getIpString(classSymbol)
  def getRepoImageName : String = Options.dockerUsername + "/" + Options.dockerRepository.toLowerCase + ":" + this.getImageName
  def getAppropriateImageName : String = if(Options.published) this.getRepoImageName else this.getImageName
  def getServiceName : String = getImageName.split("_").last
  @deprecated("use getServiceName instead.") def getJARName : String = getServiceName
  def getTempUri : java.net.URI = tempPath.toUri
  def getTempPath : Path = Paths.get(getTempUri)
  def getTempPathString : String = getTempPath.toString
  def getTempFile : File = tempPath.toFile
}
case object TempLocation{

}