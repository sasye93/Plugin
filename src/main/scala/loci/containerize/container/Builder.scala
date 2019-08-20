package loci.containerize.container

import java.io.File
import java.nio.file.StandardCopyOption.REPLACE_EXISTING
import java.nio.file.{Files, Path, Paths}

import loci.containerize.IO._
import loci.containerize.main.Containerize

import sys.process._
import loci.containerize.Options
import loci.containerize.Check
import loci.containerize.types.TempLocation

import scala.io.Source
import scala.util.Try

//todo escape vars (injection)
//todo what happens if nested execpt?
//todo apt-get working with alpine? also in external script?
//todo line endings

class Builder[+C <: Containerize](io : IO)(network : Network[C])(implicit plugin : C){

  import plugin.toolbox

  def getBuilder(dirs : List[TempLocation], buildDir : File) : build = new build(dirs, buildDir)

  class build(dirs : List[TempLocation], buildDir : File){

    //todo get proper error code
    //todo if existing, juzst update

    implicit val logger : Logger = plugin.logger

    var libraryPath : Path = _
    val plExt : String = Options.plExt
    val osExt : String = Options.osExt

    private def getRelativeContainerPath(loc : TempLocation): Path = buildDir.toPath.relativize(loc.getTempPath).normalize

    //todo make this a constructor, its first mandat. step
    def collectLibraryDependencies(dependencies : List[Path], buildDir : Path) : Option[Path] = {
      val tempLibDir = io.createDir(Paths.get(buildDir.toAbsolutePath.toString, Options.libDir)).orNull

      if(Check ? tempLibDir){
        libraryPath = tempLibDir.toPath

        import java.nio.file.StandardCopyOption._
        dependencies.foreach{ d =>
          if(!Files.exists(d))
            logger.warning(s"Could not catch library dependency stated in classpath from file system: ${d.toAbsolutePath.toString}, this could lead to missing dependency when running the container.")
          else
            Files.copy(d, Paths.get(tempLibDir.toString, d.getFileName.toString), REPLACE_EXISTING)
        }

        Some(libraryPath)
      }
      else None
    }
    def buildMANIFEST(dependencies : List[Path], directory : TempLocation) : Option[File] ={

      val CMD =
        "Manifest-Version: 1.0 \r\n" +
          "Main-Class: " + directory.entryPoint.entryClassSymbolString + " \r\n" +
          "Class-Path: " + dependencies.foldLeft("")((c, p) => c + Options.libraryPathPrefix + p.getFileName + " \r\n ") +
          "\r\n"

      io.buildFile(CMD, Paths.get(directory.getTempPathString, "MANIFEST.MF"))
    }
    def buildJARS(dependencies : List[Path]) : Unit = {
      dirs.foreach(d => {
        val workDir : File = new File(d.getTempUri)
        val manifest = buildMANIFEST(dependencies, d).orNull
        if(Process("jar -cfm ./" + d.getJARName + ".jar MANIFEST.MF -C classfiles . ", workDir).!(logger) == 0){
          manifest.delete()
          Try {
            io.recursiveClearDirectory(workDir.listFiles.find(f => f.isDirectory && f.getName == "classfiles").get, self = true)
          }
        }
      })
    }
    //todo use scala??
    def buildCMDExec() : Unit = {

      def getIpTablesCmd(d : TempLocation) : String = {
        val ports : List[String] = if(d.entryPoint.endPoints.nonEmpty) d.entryPoint.endPoints.map(_.port.toString) else List(Options.defaultContainerPort.toString)
        //todo replace back to ports
        List().foldLeft(""){ (s, port) =>
          s + (Options.platform match{
            case "windows" => s"netsh interface portproxy add v4tov4 listenport=$port listenaddress=127.0.0.1 connectport=$port connectaddress=0.0.0.0" //todo check if ok
            case _ => s"iptables -t nat -I PREROUTING -p tcp --dport $port -j DNAT --to 127.0.0.1:$port"
          }) + " \n"
        }
      }

      dirs.foreach(d => {
        val CMD =
            getIpTablesCmd(d) +
            "java -jar -Dfile.encoding=UTF-8 " + d.getJARName + ".jar"

        io.buildFile(io.buildScript(CMD), Paths.get(d.getTempPathString, s"run.$plExt"))
      })
    }

    def buildDockerFiles() : Unit = {
      if(libraryPath == null)
      //todo throw err
        1

      buildDockerBaseFile()

      dirs.foreach(d => {
        val relativeCP = getRelativeContainerPath(d).toString

        val CMD = //todo hardcoded
          s"FROM ${ Options.libraryBaseImageTag } \r\n" +
          s"LABEL ${ Options.labelPrefix }.description=" + "\"todo\" \r\n" + //todo descr macro
          s"LABEL ${ Options.labelPrefix }.version=1.0 \r\n" +  //todo vers macro
            s"WORKDIR ${ Options.containerHome } \r\n" +
            s"COPY ./run.$plExt ./*.jar ./ \r\n" +
            //"COPY [\"" + libraryPath.toString.replace("\\", "/") + "\",\"" + Options.unixLibraryPathPrefix + "\"]\r\n" +
            (if(d.entryPoint.endPoints.exists(_.way != "connect")) d.entryPoint.endPoints.foldLeft("EXPOSE")((s, e) => if(e.way == "connect") s else s + " " + e.port) else "") + "\r\n" +
            Check ?=> (d.entryPoint.setupScript,
              s"COPY ./preRunSpecific.$plExt ${Options.containerHome}preRunSpecific.$plExt \r\n" +  //todo can we just run it without copy? layers!
                s"RUN ${Options.containerHome}preRunSpecific.$plExt \r\n", "") +
            s"ENTRYPOINT ./run.$plExt \r\n"

        if(Check ? d.entryPoint.setupScript)
          Files.copy(d.entryPoint.setupScript.toPath, Paths.get(d.getTempPathString, s"preRunSpecific.$plExt"), REPLACE_EXISTING)

        io.buildFile(CMD, Paths.get(d.getTempPathString, "Dockerfile"))

        //# Copy the current directory contents into the container at /app
        //COPY . /appupickle

        //# Install any needed packages specified in requirements.txt
        //RUN pip install --trusted-host pypi.python.org -r requirements.txt



        //# Run app.py when the container launches
        //CMD ["python", "app.py"]
      })
    }
    def buildDockerBaseFile() : Unit = {
      val CMD =
        s"FROM ${Options.jreBaseImage} \r\n" +
          "ENV SCALA_VERSION=2.12.6 \r\n" +
          "WORKDIR / \r\n" +
          s"RUN mkdir -p ${ Options.containerHome } && mkdir -p ${Options.libraryPathPrefix} \r\n" +
          s"RUN apt-get update && apt-get install iptables net-tools iputils-ping -y \r\n" + //todo seems to work only with update...
          s"COPY . ${Options.libraryPathPrefix} \r\n" + //todo copy only libs
          Check ?=>[String] (Options.getSetupScript.orNull,
            s"COPY ./preRun.$plExt ${Options.libraryPathPrefix}preRun.$plExt \r\n" +  //todo can we just run it without copy? layers!
            s"RUN ${ Options.libraryPathPrefix }preRun.$plExt \r\n", "")

      Options.getSetupScript match{
        case Some(f) => Files.copy(f.toPath, Paths.get(libraryPath.toString, s"preRun.$plExt"), REPLACE_EXISTING)
        case None =>
    }
      io.buildFile(CMD, Paths.get(libraryPath.toString, "Dockerfile"))
    }
    //todo cache etc., takes long
    //todo get proper err code, res.: own DNS
    def buildDockerImages() : Unit = {

      Process(s"bash BuildBaseImage.$osExt", libraryPath.toFile).!!(logger)

      dirs.foreach{ d =>
        Process("bash " + "BuildContainer." + s"$osExt" + "\"", d.getTempFile).!!(logger)  //todo cmd is win, but not working without...? + cant get err stream because indirect
        /**
        val peerDir : File = new File(getRelativeContainerPath(d).toString)//todo check if same begin

        logger.warning(Process(s"docker build -t ${ d.getImageName } -f ${ Paths.get(peerDir.getPath, "Dockerfile") } ${peerDir.getPath}", plugin.containerDir.toPath.toFile).!.toString)
          */
      }
    }
    def buildDockerBaseImageBuildScripts() : Unit = {
      val CMD =
          s"docker build -t ${ Options.libraryBaseImageTag } -f Dockerfile . \n"
      io.buildFile(io.buildScript(CMD), Paths.get(libraryPath.toAbsolutePath.toString, s"BuildBaseImage.$osExt"))
    }
    def buildDockerImageBuildScripts() : Unit = {
      dirs.foreach { d =>
        val peerDir : File = new File(getRelativeContainerPath(d).toString)//todo check if same begin
        val dirDepth : String = "../" * (peerDir.getPath.count(c => c == '/' || c == '\\') + 1)
        val CMD =
            s"cd $dirDepth \n" +
            s"docker build -t ${ d.getImageName } -f ${ toolbox.toUnixString(peerDir.toPath) + "/Dockerfile" + " " + toolbox.toUnixString(peerDir.toPath) + "/" } \n"
        io.buildFile(io.buildScript(CMD), Paths.get(d.getTempPathString, s"BuildContainer.$osExt"))
      }
    }
    def buildDockerStartScripts() : Unit = {
      //todo we run containers => jedes mal wird neuer container created, danach nur gestoppt, existiert dann aber weiter => existing containers block creation with same name, also: possibly update instead of recreate?
      dirs.foreach { d =>
        val CMD =
          s"docker rm --volumes -f ${ d.getImageName }\n" + //todo -v flag ok? removes volume associated
          s"docker volume create ${ d.getImageName } \n" + //todo -a flag?
          s"docker run -id ${ d.entryPoint.endPoints.foldLeft("")((s, e) => if(e.way == "connect" && Check ? e.port) s else s + s"--publish ${ e.port }:${ e.port }") } --name ${ d.getImageName } --network=${ network.getName } --mount source=${ d.getImageName },target=${ Options.containerVolumeStore } --cap-add=NET_ADMIN --cap-add=NET_RAW --sysctl net.ipv4.conf.eth0.route_localnet=1 -t ${ d.getImageName } \n" +
          "docker container inspect -f \"Container '" + d.getImageName + "' connected to " + network.getName + " with ip={{range .NetworkSettings.Networks}}{{.IPAddress}}{{end}}.\" " + d.getImageName + "\n"
        io.buildFile(io.buildScript(CMD), Paths.get(d.getTempPathString, s"RunContainer.$osExt"))//todo add -p, .sh
      }
    }
    def buildDockerStopScripts() : Unit = {
      dirs.foreach { d =>
        val CMD =
            s"docker network disconnect ${ network.getName } ${ d.getImageName }\n" +
            s"docker stop ${ d.getImageName } \n" +
            s"docker container rm -f ${ d.getImageName }"
        io.buildFile(io.buildScript(CMD), Paths.get(d.getTempPathString, s"StopContainer.$osExt"))
      }
    }

    def publishDockerImagesToRepo() : Unit = {
      dirs.foreach{ d =>
        val imageTag = s"${ Options.dockerUsername }/${ Options.dockerRepository.toLowerCase }:${ d.getImageName }"
        Process(s"docker tag ${ d.getImageName } $imageTag").#&&(Process(s"docker push $imageTag")).!(logger)
      }
    }
    def saveImageBackups(tags : List[String]): Unit = {
      tags.foreach{
        tag =>
          Process(s"docker save -o ${ tag }.tar ${ tag }").!(logger)
      }
    }

    def createReadme(buildDir : Path) : Unit = {
      if(Check ! io.buildFile(Source.fromInputStream(getClass.getResourceAsStream("/readme.html")).mkString, Paths.get(buildDir.toAbsolutePath.toString, "readme.html")).orNull)
        logger.warning("Could not create readme file.")
    }
  }
}
