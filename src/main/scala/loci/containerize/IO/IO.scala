package loci.containerize.IO

import java.io.{BufferedReader, BufferedWriter, File, FileInputStream, FileOutputStream, FileReader, FileWriter, IOException, ObjectInputStream, ObjectOutputStream}
import java.nio.CharBuffer
import java.nio.file.StandardCopyOption.REPLACE_EXISTING
import java.nio.file.{CopyOption, FileAlreadyExistsException, Files, Path, Paths}

import loci.containerize.{Check, Options}
import loci.containerize.main.Containerize
import loci.containerize.types.TempLocation

import scala.io.{BufferedSource, Source}

//todo try catch
class IO(implicit val logger : Logger) {

  def serialize(obj : Serializable, path : Path) : Unit = {
    var oos : ObjectOutputStream = null
    try{
      oos = new ObjectOutputStream(new FileOutputStream(path.toString))
      oos.writeObject(obj)
      oos.close()
    }
    catch{
      case e @ (_ : FileAlreadyExistsException |
                _ : UnsupportedOperationException |
                _ : SecurityException |
                _ : IOException) => logger.error("Deserialization error: " + e.getMessage + s" (tried to serialize object ${obj}).")
      case e : Throwable => logger.error("Serialization error: " + e.getMessage)
    }
    finally{
      if(oos != null)
        oos.close()
    }
  }
  def deserialize[T](path : Path) : Option[T] = {
    var ois : ObjectInputStream = null
    try{
      ois = new ObjectInputStream(new FileInputStream(path.toString))
      val obj : Object = ois.readObject()
      val o : T = obj match{ //todo does not work, class cast is not catched!
        case _ : T => obj.asInstanceOf[T]
        case _ => throw new IOException(s"Wrong object type when deserializing: ${obj}")
      }
      ois.close()
      return Some(o)
    }
    catch{
      case e @ (_ : UnsupportedOperationException |
                _ : SecurityException |
                _ : IOException) => logger.error("Deserialization error: " + e.getMessage + s" (tried to serialize object from ${path}).")
      case e : Throwable => logger.error("Deserialization error: " + e.getMessage)
      case e : java.lang.Throwable => logger.error("Deserialization error: " + e.getMessage)
    }
    finally{
      if(ois != null)
        ois.close()
    }
    None
  }
  def readFromFile(path : Path) : String = readFromFile(new File(path.toUri))
  def readFromFile(file : File) : String = {
    var bs : BufferedSource = null
    try{
      if(!(file.exists && file.isFile))
        throw new IOException(s"Cannot open file ${file.getPath}.")

      bs = Source.fromFile(file)
      return bs.getLines.foldLeft("")((s, l) => s + l)
    }
    catch{
      case e @ (_ : FileAlreadyExistsException |
                _ : UnsupportedOperationException |
                _ : SecurityException |
                _ : IOException) => logger.error(e.getMessage + s" (tried to read file ${file.getPath}).")
      case e : Throwable => logger.error(e.getMessage)
    }
    finally{
      if(bs != null)
        bs.close
    }
    ""
  }

  def buildFile(content : String, path : Path) : Option[File] = {
    var bw : BufferedWriter = null
    try{
      val file : File = new File(path.toUri)

      if(!file.getParentFile.exists())
        createDirRecursively(file.getParentFile.toPath)

      if(file.exists())
        file.delete()
      file.createNewFile()

      bw = new BufferedWriter(new FileWriter(file))
      bw.write(content)
      return Some(file)
    }
    catch{
      case e @ (_ : FileAlreadyExistsException |
                _ : UnsupportedOperationException |
                _ : SecurityException |
                _ : IOException) => logger.error(e.getMessage + s" (tried to build file ${path.getParent.toString}).")
      case e : Throwable => logger.error(e.getMessage)
    }
    finally{
      if(bw != null)
        bw.close()
    }
    None
  }
  def createDir(path : Path) : Option[File] = {
    try{
      val f : File = path.toFile
      if(f.exists())
        return if(f.isDirectory) Some(f) else None
      return Some(Files.createDirectory(path).toFile)
    }
    catch{
      case e @ (_ : FileAlreadyExistsException |
                _ : UnsupportedOperationException |
                _ : SecurityException |
                _ : IOException) => logger.error(e.getMessage + s" (tried to create dir ${path.toString}).")
      case e : Throwable => logger.error(e.getMessage)
    }
    None
  }
  def createDirRecursively(createPath : Path) : Option[File] = {
    try{
      val f : File = new File(createPath.toUri)
      if(f.isDirectory)
        return Some(f)
      else
        return Some(Files.createDirectories(createPath).toFile)
    }
    catch{
      case e @ (_ : FileAlreadyExistsException |
                _ : UnsupportedOperationException |
                _ : SecurityException |
                _ : IOException) => logger.error(e.getMessage + s" (tried to create dir recursively ${createPath.toString}).")
      case e : Throwable => logger.error(e.getMessage)
    }
    None
  }
  def recursiveClearDirectory(dir : File, self : Boolean = false) : Unit = {
    import scala.reflect.io.Directory
    try{
      if(dir.isDirectory){
        dir.listFiles(f => if(f.isDirectory) Directory(f).deleteRecursively() else f.delete())
        if(self)
          dir.delete()
      }
    }
    catch{
      case e @ (_ : FileAlreadyExistsException |
                _ : UnsupportedOperationException |
                _ : SecurityException |
                _ : IOException) => logger.error(e.getMessage + s" (tried to clear dir recursively ${dir.getPath}).")
      case e : Throwable => logger.error(e.getMessage)
    }
  }

  @deprecated("")
  def clearTempDirs(dirs : List[TempLocation]) : Unit = dirs match{
    case head :: tail =>

      val files = new File(head.getTempUri).listFiles()
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
    }
    catch{
      case e @ (_ : FileAlreadyExistsException |
                _ : UnsupportedOperationException |
                _ : SecurityException |
                _ : IOException) => logger.error(e.getMessage + s" (tried to clear dir recursively ${dir.toString}).")
      case e : Throwable => logger.error(e.getMessage)
    }
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
  def buildScript(CMD : String) : String  = "#!/bin/sh\n" + CMD.replaceAll("\\r\\n", "\n") //todo shall we support bat?
}
