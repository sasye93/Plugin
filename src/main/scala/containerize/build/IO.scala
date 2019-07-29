package containerize.build

import java.io.{File, IOException}
import java.nio.file.{Files, Path}

import containerize.types.TempLocation


//todo try catch
object IO {
  def clearTempDirs(dirs : List[TempLocation]) : Unit = dirs match{
    case head :: tail =>

      val files = new File(head.tempPath.toUri).listFiles()
      files.filterNot(_.getName == (head.classSymbol + ".jar")).foreach(_.delete())

      IO.clearTempDirs(tail)
    case Nil =>
  }
  def createFolderStructure(createPath : Path) : Unit = {
    try{
      if(!new File(createPath.toUri).isDirectory)
        Files.createDirectories(createPath)
    }/*
    catch{
      case e : SecurityException => //todo
      case e : IOException => //todo
      case e : Throwable => //todo

    }*/
    finally{
    }
  }
}
