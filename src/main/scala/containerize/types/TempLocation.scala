package containerize.types

import java.nio.file.Path


case class TempLocation(classSymbol : String, tempPath : Path, entryPoint : collection.immutable.HashMap[String, String]){
  def getImageName : String = classSymbol.toLowerCase.replace("$", ".")//todo what i flast char is $, invlaid
}
