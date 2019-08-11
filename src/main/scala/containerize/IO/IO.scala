package containerize.IO

import java.io.File
import java.nio.file.{CopyOption, Files, Path, Paths}

import containerize.main.Containerize
import containerize.types.TempLocation

//todo try catch
class IO(parent : Containerize) {
  def clearTempDirs(dirs : List[TempLocation]) : Unit = dirs match{
    case head :: tail =>

      val files = new File(head.tempPath.toUri).listFiles()
      files.filterNot(_.getName == (head.classSymbol + ".jar")).foreach(_.delete())

      this.clearTempDirs(tail)
    case Nil =>
  }
  def copyContentOfDirectory(dir : Path, to : Path, recursive : Boolean = true, excludePrefix : String = null) : Unit = {
    try{
      val d : File = new File(dir.toUri)
      val t : File = new File(to.toUri)
      if(d.exists() && d.isDirectory){
        import java.nio.file.StandardCopyOption._
        if(!t.exists())
          t.mkdir()
        else if(!t.isDirectory){
          t.delete()
          t.mkdir()
        }
        d.listFiles().foreach(f =>
          if(excludePrefix == null || !f.getName.startsWith(excludePrefix)){
            if(f.isDirectory)
              copyContentOfDirectory(f.toPath, Paths.get(t.getAbsolutePath, f.getName), recursive)
            else
              Files.copy(Paths.get(d.getAbsolutePath, f.getName), Paths.get(t.getAbsolutePath, f.getName), REPLACE_EXISTING)
          })
      }
    }/*
    catch{
      case e : SecurityException => //todo
      case e : IOException => //todo
      case e : Throwable => //todo

    }*/
    finally{
    }
  }
  @deprecated("does this work???", "")
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
  def listDependencies(repository : Path) : List[Path] = {
    val f : File = repository.toFile
    if(!f.exists)
      return List()
    else if(!f.canRead)
      return null //todo err

    if(f.isFile)
      List[Path](f.toPath)

    else
      f.listFiles().flatMap(file => listDependencies(file.toPath)).toList
  }
}
