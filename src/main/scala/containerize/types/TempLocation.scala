package containerize.types

import java.nio.file.Path

import loci.container.SimplifiedContainerEntryPoint

case class TempLocation(classSymbol : String, tempPath : Path, entryPoint : SimplifiedContainerEntryPoint){
  def getImageName : String = classSymbol.toLowerCase.replace("$", ".")//todo what i flast char is $, invlaid
  def getJARName : String = { val name = getImageName.split('.'); name(name.length-1) }
}
