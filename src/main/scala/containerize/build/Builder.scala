package containerize.build

import java.io.IOException
import java.io.File
import java.io.FileWriter
import java.io.BufferedWriter
import java.nio.file.StandardCopyOption.REPLACE_EXISTING
import java.nio.file.{Files, Path, Paths}

import containerize.IO.Logger
import containerize.main.Containerize

import sys.process._
import containerize.Options
import containerize.Check
import containerize.types.TempLocation

//todo escape vars (injection)
//todo what happens if nested execpt?

class Builder[+C <: Containerize](val parent : C)(logger : Logger){


  def getBuilder(dirs : List[TempLocation], workDir : Path) : build = new build(dirs, workDir)

  class build(dirs : List[TempLocation], workDir : Path){

    //todo get proper error code
    //todo if existing, juzst update

    var libraryPath : Path = _
    val ext : String = if (Options.platform == "windows") "bat" else "sh"

    private def getRelativeContainerPath(containerDir : TempLocation): Path = workDir.relativize(containerDir.tempPath).normalize

    //todo make this a constructor, its first mandat. step
    def collectLibraryDependencies(dependencies : List[Path]): Path ={
      val tempDir = Files.createTempDirectory(workDir, "_LOCI_CONTAINERIZE_LIB_TEMP").toFile

      val tempLibDir = new File(tempDir + "\\" + "libs")

      if(!tempLibDir.mkdir())
        logger.error("Could not create temporary lib directory.")

      libraryPath = tempLibDir.toPath

      import java.nio.file.StandardCopyOption._
      dependencies.foreach{ d =>
        if(!Files.exists(d))
          logger.warning(s"Could not catch library dependency stated in classpath from file system: ${d.toAbsolutePath.toString}, this could leed to missing dependency when running the container.")
        else
          Files.copy(d, Paths.get(tempLibDir.toString, d.getFileName.toString), REPLACE_EXISTING)
      }

      tempDir.delete()
      libraryPath
    }
    def buildMANIFEST(dependencies : List[Path], directory : TempLocation): Unit ={

      val CMD =
        "Manifest-Version: 1.0 \r\n" +
          "Main-Class: " + directory.entryPoint.entryClassSymbolString + " \r\n" +
          "Class-Path: " + dependencies.foldLeft("")((c, p) => c + Options.libraryPathPrefix + p.getFileName + " \r\n ") +
          "\r\n"

      var bw : BufferedWriter = null
      try{
        val manifestFile : File = new File(Paths.get(directory.tempPath.toString, "MANIFEST.MF").toUri)

        if(manifestFile.exists())
          manifestFile.delete()
        manifestFile.createNewFile()

        bw = new BufferedWriter(new FileWriter(manifestFile))
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
    }
    def buildJARS(dependencies : List[Path]) : Unit = {
      dirs.foreach(d => {
        val workDir : File = new File(d.tempPath.toUri)
        buildMANIFEST(dependencies, d)
        Process("jar -cfm ./" + d.getJARName + ".jar MANIFEST.MF -C classfiles . ", workDir).!
      })
    }
    //todo use scala??
    def buildCMDExec() : Unit = {
      dirs.foreach(d => {
        val CMD =
          s"${ if (Options.platform == "linux") "#!/bin/sh \n" }" + "java -jar -Dfile.encoding=UTF-8 " + d.getJARName + ".jar"

        var bw : BufferedWriter = null
        try{
          val batFile : File = new File(Paths.get(d.tempPath.toString, s"run.$ext").toUri)

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

    def buildDockerFiles() : Unit = {
      if(libraryPath == null)
      //todo throw err
        1

      buildDockerLibraryBaseFile()

      dirs.foreach(d => {
        val relativeCP = getRelativeContainerPath(d).toString

        val CMD = //todo hardcoded
            s"FROM ${ Options.libraryBaseImageTag } \r\n" +
            "RUN echo \"building docker file for peer " + d.classSymbol + "\" \r\n" +
            "MAINTAINER simon.schoenwaelder@gmx.de \r\n" +
            "ENV SCALA_VERSION=2.12.6 \r\n" +
            "WORKDIR / \r\n" +
            "RUN mkdir -p /app \r\n" +
            "WORKDIR /app \r\n" +
            s"COPY ./run.$ext ./*.jar ./ \r\n" +
            //"COPY [\"" + libraryPath.toString.replace("\\", "/") + "\",\"" + Options.unixLibraryPathPrefix + "\"]\r\n" +
            s"EXPOSE${ if(d.entryPoint.endPoints.nonEmpty) d.entryPoint.endPoints.foldLeft("")((s, e) => s + " " + e.port ) else Options.defaultContainerPort } \r\n" +
            s"ENTRYPOINT ./run.$ext \r\n"

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
    def buildDockerLibraryBaseFile() : Unit = {
      val CMD =
        s"FROM ${Options.jreBaseImage} \r\n" +
        s"RUN mkdir -p ${Options.libraryPathPrefix} \r\n" +
          Check ?=> (Options.setupScript,
            s"COPY ./preRun.$ext ${Options.libraryPathPrefix}preRun.$ext \r\n" +
            s"CMD ${ Options.libraryPathPrefix }preRun.$ext \r\n", "") +
        s"COPY . ${Options.libraryPathPrefix} \r\n" //todo copy only libs

      var bw : BufferedWriter = null
      try{
        val dockerFile : File = new File(Paths.get(libraryPath.toString, "Dockerfile").toUri)

        if(dockerFile.exists())
          dockerFile.delete()
        dockerFile.createNewFile()

        if(Check ? Options.setupScript)
          Files.copy(Options.setupScript.toPath, Paths.get(libraryPath.toString, s"preRun.$ext"), REPLACE_EXISTING)

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
    }
    //todo cache etc., takes long
    //todo get proper err code, res.: own DNS
    def buildDockerImages() : Unit = {
      Process(s"docker build -t ${ Options.libraryBaseImageTag } -f Dockerfile .", libraryPath.toFile).!(logger.strong)
      dirs.foreach{ d =>
        val peerDir : File = new File(getRelativeContainerPath(d).toString)//todo check if same begin
        logger.warning(s"docker build -patht "+peerDir.getPath)
        logger.warning(s"docker build -t ${ d.getImageName } -f ${ Paths.get(peerDir.getPath, "Dockerfile") } . +++"+ workDir)
        logger.warning(Process(s"docker build -t ${ d.getImageName } -f ${ Paths.get(peerDir.getPath, "Dockerfile") } ${peerDir.getPath}", workDir.toFile).!.toString)
      }
    }

    def publishImagesToRepo() : Unit = {
      dirs.foreach{ d =>
        val imageTag = s"${ Options.dockerUsername }/${ Options.dockerRepository.toLowerCase }:${ d.getImageName }"
        Process(s"docker tag ${ d.getImageName } $imageTag").#&&(Process(s"docker push $imageTag")).!(logger)
      }
    }

    def buildDockerCompose() : Unit = {
      val file =
        "version: \"3\"\nservices:\n  web:\n    # replace username/repo:tag with your name and image details\n    image: username/repo:tag\n    deploy:\n      replicas: 5\n      resources:\n        limits:\n          cpus: \"0.1\"\n          memory: 50M\n      restart_policy:\n        condition: on-failure\n    ports:\n      - \"4000:80\"\n    networks:\n      - webnet\nnetworks:\n  webnet:"
    }
  }
  object saver{

    def saveImageBackups(tags : List[String]): Unit = {
      tags.foreach{
        tag =>
          Process(s"docker save -o ${ tag }.tar ${ tag }").!(logger)
      }
    }
  }
}
