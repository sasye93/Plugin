package loci.containerize.container

import java.io.File
import java.nio.file.StandardCopyOption.REPLACE_EXISTING
import java.nio.file.{Files, Path, Paths}

import loci.containerize.AST.DependencyResolver
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

class Builder(io : IO)(implicit plugin : Containerize){

  import plugin.global
  import plugin.toolbox

  def getBuilder(dirs : Map[String, List[TempLocation]], buildDir : File) : build = new build(dirs, buildDir)

  class build(dirs : Map[String, List[TempLocation]], buildDir : File){

    //todo get proper error code
    //todo if existing, juzst update

    implicit val logger : Logger = plugin.logger

    private val dependencyResolver : DependencyResolver = new DependencyResolver()

    var libraryPath : Path = _
    val plExt : String = Options.plExt
    val osExt : String = Options.osExt

    private def getRelativeContainerPath(loc : TempLocation): Path = buildDir.toPath.relativize(loc.getTempPath).normalize

    //todo make this a constructor, its first mandat. step
    def collectLibraryDependencies(buildDir : Path) : Option[Path] = {
      val tempLibDir = io.createDir(Paths.get(buildDir.toAbsolutePath.toString, Options.libDir)).orNull
      val dependencies : List[Path] = dependencyResolver.classPathDependencies()

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
    def buildMANIFEST(directory : TempLocation) : Option[File] ={

      val dependencies : List[String] = dependencyResolver.classPathDependencies().map(Options.libraryPathPrefix + _.getFileName) ++ dependencyResolver.classJRELibs()
      val CMD =
        s"""Manifest-Version: 1.0
           |Main-Class: ${ directory.entryPoint.entryClassSymbolString }
           |""" +
          dependencies.foldLeft("Class-Path: ")((C, p) => C + p + " \n ") + "\n"

      io.buildFile(CMD.stripMargin, Paths.get(directory.getTempPathString, "MANIFEST.MF"))
    }
    def buildJARS() : Unit = {
      dirs.flatMap(_._2).foreach(d => {
        val workDir : File = new File(d.getTempUri)
        val manifest = buildMANIFEST(d).orNull
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

      dirs.flatMap(_._2).foreach(d => {
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

      dirs.flatMap(_._2).foreach(d => {
        val relativeCP = getRelativeContainerPath(d).toString
        val ports = d.entryPoint.endPoints.filter(_.way != "connect").map(_.port).toSet
        val script = d.entryPoint.config.getScript

        val CMD = //todo hardcoded //todo vers macro //todo descr macro
          s"""FROM ${ Options.libraryBaseImageTag }
          |LABEL ${ Options.labelPrefix }.description="todo"
          |LABEL ${ Options.labelPrefix }.version=1.0
          |WORKDIR ${ Options.containerHome }
          |COPY ./run.$plExt ./*.jar ./
          |""" +
            (if(d.entryPoint.isGateway && ports.nonEmpty) ports.foldLeft("EXPOSE")((S, port) => S + " " + port) else "") + "\n" +
            Check ?=> (script.orNull,  //todo can we just run it without copy? layers!
              s"COPY ./preRunSpecific.$plExt ${Options.containerHome}/preRunSpecific.$plExt \n" +
                s"RUN ${Options.containerHome}/preRunSpecific.$plExt \n", "") +
            s"ENTRYPOINT ./run.$plExt \n"
            //"COPY [\"" + libraryPath.toString.replace("\\", "/") + "\",\"" + Options.unixLibraryPathPrefix + "\"]\r\n" +

         //todo ok?
        if(script.isDefined)
          Files.copy(script.get.toPath, Paths.get(d.getTempPathString, s"preRunSpecific.$plExt"), REPLACE_EXISTING)

        io.buildFile(CMD.stripMargin, Paths.get(d.getTempPathString, "Dockerfile"))

        //# Copy the current directory contents into the container at /app
        //COPY . /appupickle

        //# Install any needed packages specified in requirements.txt
        //RUN pip install --trusted-host pypi.python.org -r requirements.txt



        //# Run app.py when the container launches
        //CMD ["python", "app.py"]
      })
    }
    def buildDockerBaseFile() : Unit = {//todo vers ok? //todo apt-get seems to work only with update... //todo copy only libs
      val CMD = //todo COPY --from=0 / / ? or whatever, hauptsache multiple builds work
        s"""FROM ${Options.jreBaseImage} AS jre-build
           ${ Options.dbBaseImage match{ case Some(db) => s"|COPY --from=$db / /" case None => "" } }
           ${ Options.customBaseImage match{ case Some(custom) => s"|COPY --from=$custom / /" case None => "" } }
           |ENV SCALA_VERSION=${ util.Properties.versionNumberString }
           |WORKDIR /
           |RUN apt-get update && apt-get install iptables net-tools iputils-ping -y
           |RUN groupadd -r ${Options.swarmName} && useradd --no-log-init -r -g ${Options.swarmName} ${Options.swarmName}
           |RUN mkdir -p ${Options.containerHome} && mkdir -p ${Options.libraryPathPrefix} && chown ${Options.swarmName}:${Options.swarmName} ${Options.containerHome} && chown ${Options.swarmName}:${Options.swarmName} ${Options.libraryPathPrefix}
           |USER ${Options.swarmName}:${Options.swarmName}
           |COPY . ${Options.libraryPathPrefix}
           |""" +
          Check ?=>[String] (Options.getSetupScript.orNull,
            s"COPY ./preRun.$plExt ${Options.libraryPathPrefix}preRun.$plExt \n" +  //todo can we just run it without copy? layers!
            s"RUN ${ Options.libraryPathPrefix }preRun.$plExt \n", "")

      Options.getSetupScript match{
        case Some(f) => Files.copy(f.toPath, Paths.get(libraryPath.toString, s"preRun.$plExt"), REPLACE_EXISTING)
        case None =>
      }
      io.buildFile(CMD.stripMargin, Paths.get(libraryPath.toString, "Dockerfile"))
    }
    //todo cache etc., takes long
    //todo get proper err code, res.: own DNS
    def buildDockerImages() : Unit = {

      Process(s"bash BuildBaseImage.$osExt", libraryPath.toFile).!!(logger)

      dirs.flatMap(_._2).foreach{ d =>
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
      dirs.flatMap(_._2).foreach { d =>
        val peerDir : File = new File(getRelativeContainerPath(d).toString)//todo check if same begin
        val dirDepth : String = "../" * (peerDir.getPath.count(c => c == '/' || c == '\\') + 1)
        val CMD =
            s"cd $dirDepth \n" +
            s"docker build -t ${ d.getImageName } -f ${ toolbox.toUnixString(peerDir.toPath) + "/Dockerfile" + " " + toolbox.toUnixString(peerDir.toPath) + "/" } \n"
        io.buildFile(io.buildScript(CMD), Paths.get(d.getTempPathString, s"BuildContainer.$osExt"))
      }
    }
    def buildDockerRunScripts() : Unit = {
      //todo we run containers => jedes mal wird neuer container created, danach nur gestoppt, existiert dann aber weiter => existing containers block creation with same name, also: possibly update instead of recreate?
      val globalNet = new Network(plugin.io)(Options.swarmName, buildDir.toPath)
      globalNet.buildSetupScript()
      globalNet.buildNetwork()
      dirs.keys.foreach{ module =>
        val moduleNet = new Network(plugin.io)(module, buildDir.toPath)

        dirs.getOrElse(module, List()).foreach{ loc =>

//todo network switch for global net on off
          val CMDStart = //todo -v flag ok? removes volume associated //todo -a flag?
            s"""docker rm --volumes -f ${ loc.getImageName }
               |docker volume create ${ loc.getImageName }
               |docker run -id ${ if(loc.entryPoint.isGateway) loc.entryPoint.endPoints.filter(_.way != "connect").map(_.port).toSet.foldLeft("")((S, port) => S + s"--publish ${ port }:${ port } ") else "" } --name ${ loc.getImageName } --network=${ globalNet.getName } --mount source=${ loc.getImageName },target=${ Options.containerVolumeStore } --cap-add=NET_ADMIN --cap-add=NET_RAW --sysctl net.ipv4.conf.eth0.route_localnet=1 -t ${ loc.getImageName }
               |docker network connect --alias ${ loc.getImageName } ${ moduleNet.getName } ${ loc.getImageName }
               |docker container inspect -f \"Container '""".stripMargin + loc.getImageName + "' connected to " + globalNet.getName + " and " + moduleNet.getName + " with ip={{range .NetworkSettings.Networks}}{{.IPAddress}}{{end}}.\" " + loc.getImageName + "\n"

          val CMDStop =
            s"""docker network disconnect ${ globalNet.getName } ${ loc.getImageName }
               |docker network disconnect ${ moduleNet.getName } ${ loc.getImageName }
               |docker stop ${ loc.getImageName }
               |docker container rm -f ${ loc.getImageName }""".stripMargin

          io.buildFile(io.buildScript(CMDStart), Paths.get(loc.getTempPathString, s"RunContainer.$osExt"))//todo add -p, .sh
          io.buildFile(io.buildScript(CMDStop), Paths.get(loc.getTempPathString, s"StopContainer.$osExt"))
        }
        moduleNet.buildSetupScript()
        moduleNet.buildNetwork()
      }
    }

    def publishDockerImagesToRepo() : Unit = {
      dirs.flatMap(_._2).foreach{ d =>
        val imageTag = s"${ Options.dockerUsername }/${ Options.dockerRepository.toLowerCase }:${ d.getImageName }"
        (Process(s"docker tag ${ d.getImageName } $imageTag") #&& Process(s"docker push $imageTag")).!(logger)
      }
    }
    def saveImageBackups(): Unit = {
      io.createDir(Paths.get(plugin.homeDir.getPath, Options.backupDir))
      dirs.flatMap(_._2).foreach{ d =>
          Process(s"docker save -o ${ Paths.get(Options.backupDir, d.getImageName) }.tar ${ d.getImageName }", plugin.homeDir).!(logger)
      }
    }

    def createReadme(buildDir : Path) : Unit = {
      /** todo
      io.buildFile(Source.fromResource("/readme.html", this.getClass.getClassLoader).mkString, Paths.get(buildDir.toAbsolutePath.toString, "readme.html")) match{
        case Some(readme) =>
          dirs.foreach( d => Try{ Files.copy(readme.toPath.toAbsolutePath, Paths.get(d.getTempPathString, readme.getName)) })
          readme.delete()
        case None => logger.warning("Could not create readme file.")
      }*/
    }
  }
}
