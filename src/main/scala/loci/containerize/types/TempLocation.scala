package loci.containerize.types

import java.nio.file.Path

import scala.annotation.meta.{getter, setter}

case class TempLocation(classSymbol : String, tempPath : Path, entryPoint : SimplifiedContainerEntryPoint){
  def getImageName : String = loci.container.Tools.getIpString(classSymbol)
  def getJARName : String = { val name = getImageName.split('.'); name(name.length-1) }

}
case object TempLocation{

}