/**
  * Temp location class, serves as a wrapper for generated images.
  * @author Simon Schönwälder
  * @version 1.0
  */
package loci.impl.types

import java.io.File
import java.nio.file.{Path, Paths}

import loci.impl.Options

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