/**
  * IO class, provides read/write capabilities.
  * @author Simon Schönwälder
  * @version 1.0
  */
package loci.container.build.IO

import java.io.{BufferedWriter, DataOutputStream, File, FileOutputStream, FileWriter, IOException}
import java.nio.file.{FileAlreadyExistsException, Files, Path, Paths}

import loci.container.build.{Check, Options}

import scala.io.{BufferedSource, Source}
import scala.reflect.io.Directory

import upickle.default._

class IO(implicit val logger : Logger) {

  def serialize[T <: Pickle](obj : Pickle, path : Path) : Unit = {
    try{
      val json = write(obj)
      Files.write(path, json.getBytes)
    }
    catch{
      case e @ (_ : FileAlreadyExistsException |
                _ : UnsupportedOperationException |
                _ : SecurityException |
                _ : IOException) => logger.error("Serialization error: " + e.getMessage + s" (tried to serialize object ${obj}).")
      case e : Throwable => logger.error("Serialization error: " + e.getMessage)
    }
  }
  def deserialize[T <: Pickle](path : Path) : Option[T] = {
    try{
      val f : File = path.toFile
      val obj : Pickle = upickle.default.read[Pickle](f)
      val o = obj match{
        case _ : T => obj.asInstanceOf[T]
        case _ => throw new IOException(s"Wrong object type when de-serializing: ${obj}")
      }
      return Some(o)
    }
    catch{
      case e @ (_ : UnsupportedOperationException |
                _ : SecurityException |
                _ : IOException) => logger.error("Deserialization error: " + e.getMessage + s" (tried to serialize object from ${path}).")
      case e : Throwable => logger.error("Deserialization error: " + e.getMessage)
    }
    None
  }
  def readFromFile(path : Path) : String = readFromFile(new File(path.toUri))
  def readFromFile(file : File, binary : Boolean = false) : String = {
    var bs : BufferedSource = null
    try{
      if(!(file.exists && file.isFile))
        throw new IOException(s"Cannot open file ${file.getPath}.")

      bs = Source.fromFile(file)

      return bs.getLines mkString "\n"
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
  def buildFile(content : String, path : Path, binary : Boolean = false, unix : Boolean = false) : Option[File] = {
    var bw : BufferedWriter = null
    var os : DataOutputStream = null
    try{
      val file : File = new File(path.toUri)

      if(!file.getParentFile.exists())
        createDirRecursively(file.getParentFile.toPath)

      if(file.exists())
        file.delete()
      file.createNewFile()

      val con = if(unix) Options.toolbox.toUnixFile(content) else content

      if(binary){
        os = new DataOutputStream(new FileOutputStream(file))
        os.write(con.getBytes())
      }
      else{
        bw = new BufferedWriter(new FileWriter(file))
        bw.write(con)
      }

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
      if(os != null)
        os.close()
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
  }
  def buildScript(CMD : String) : String  = "#!/bin/sh\n" + Options.toolbox.toUnixFile(CMD)

  def resolvePath(p : Path)(implicit logger : Logger) : Option[File] = if(p != null) resolvePath(p) else None
  def resolvePath(p : String, homeDir : String = null)(implicit logger : Logger) : Option[File] = {
    try{
      Path.of(p) match{
        case null => None
        case p if !p.isAbsolute && Check ? homeDir => Some(Paths.get(homeDir.toString, p.toString).toFile)
        case p if !p.isAbsolute => logger.error(s"You can only supply a relative path to a file if you first set your home directory with the 'home' option in your module config, otherwise you must supply an absolute path: ${p.toString}."); None
        case p @ _ => Some(p.toFile)
      }
    }
    catch{
      case _ : java.nio.file.InvalidPathException => None
      case _ : Throwable => None
    }
  }
  def checkFile(path : Path) : Boolean = {
    val exists = Files.exists(path)
    if(!exists) logger.error(s"Could not find file: ${path}")
    exists
  }
  def checkFile(path : String) : Boolean = checkFile(Path.of(path))
}
