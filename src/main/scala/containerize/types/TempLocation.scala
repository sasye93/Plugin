package containerize.types

import java.nio.file.Path

case class TempLocation(classSymbol : String, tempPath : Path, entryPoint : String)
