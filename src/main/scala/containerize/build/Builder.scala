package containerize.build

import java.io.IOException
import java.io.File
import java.io.FileWriter
import java.io.BufferedWriter
import java.nio.file.Paths

import scala.tools.nsc.reporters.Reporter
import sys.process._
import containerize.main._
import containerize.types.TempLocation

//todo escape vars (injection)
//todo what happens if nested execpt?

class Builder(dirs : List[TempLocation], reporter : Reporter){
  //todo get proper error code
  def buildJARS() : Unit = {
    dirs.foreach(d => {
      val workDir : File = new File(d.tempPath.toUri)
      Process("jar -cfe ./" + d.classSymbol + ".jar " + d.entryPoint + " .", workDir).!
    })
  }
  //todo use scala
  def buildCMDExec(classPath : String) : Unit = {
      dirs.foreach(d => {
        val CMD =
          "#!/bin/sh \n" +
          "java " +
            "-Dfile.encoding=UTF-8 " +
            "-classpath \"" + classPath + ";" + d.classSymbol + ".jar" + "\"" + " " +
            d.entryPoint

        var bw : BufferedWriter = null
        try{
          val batFile : File = new File(Paths.get(d.tempPath.toString, "run.bat").toUri)

          if(batFile.exists())
            batFile.delete()
          batFile.createNewFile()

          bw = new BufferedWriter(new FileWriter(batFile))
          bw.write(CMD)
        }
        catch{
          case e : IOException => //todo
          case e : Throwable => //todo
        }
        finally{
          if(bw != null)
            bw.close()
        }
      })
  }

  def buildDockerFiles(): Unit = {
    dirs.foreach(d => {
      val CMD =
        "FROM openjdk:8-jre-alpine \r\n" +
          "RUN echo \"building docker file for peer " + d.classSymbol + "\" \r\n" +
          "RUN mkdir -p /app \r\n" +
          "WORKDIR /app \r\n" +
          "COPY ./run.bat ./*.jar /app/ \r\n" +
          "EXPOSE 80 \r\n" +
          "ENV NAME XXX \r\n" +
          "ENTRYPOINT [\"./run.bat\"] \r\n"

      var bw : BufferedWriter = null
      try{
        val dockerFile : File = new File(Paths.get(d.tempPath.toString, "Dockerfile").toUri)

        if(dockerFile.exists())
          dockerFile.delete()
        dockerFile.createNewFile()

        bw = new BufferedWriter(new FileWriter(dockerFile))
        bw.write(CMD)
      }
      catch{
        case e : SecurityException => //todo
        case e : IOException => //todo
        case e : Throwable => //todo
      }
      finally{
        if(bw != null)
          bw.close()
      }

      //# Copy the current directory contents into the container at /app
      //COPY . /appupickle

      //# Install any needed packages specified in requirements.txt
      //RUN pip install --trusted-host pypi.python.org -r requirements.txt



      //# Run app.py when the container launches
      //CMD ["python", "app.py"]
    })
  }
  //todo cache etc., takes long
  //todo get proper err code, res.: own DNS
  def buildDockerImages() : Unit = {
    dirs.foreach(d => {
      val workDir : File = new File(d.tempPath.toUri)
      reporter.warning(null, Process("docker build -t scala-app ./", workDir).!.toString)
    })
  }
}
