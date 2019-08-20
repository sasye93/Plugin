package loci.containerize.types

import java.io.File
import java.nio.file.{Path, Paths}

import scala.annotation.meta.{getter, setter}

case class TempLocation(classSymbol : String, private val tempPath : Path, entryPoint : SimplifiedContainerEntryPoint){
  def getImageName : String = loci.container.Tools.getIpString(classSymbol)
  def getJARName : String = { val name = getImageName.split('.'); name(name.length-1) }
  def getTempUri : java.net.URI = tempPath.toUri
  def getTempPath : Path = Paths.get(getTempUri)
  def getTempPathString : String = getTempPath.toString
  def getTempFile : File = tempPath.toFile
}
case object TempLocation{

}