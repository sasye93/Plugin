/**
  * Builder class, builds images, containers, scripts.
  * @author Simon Schönwälder
  * @version 1.0
  */
package loci.impl.container

import java.io.File
import java.nio.file.{Files, Path, Paths}
import java.nio.file.StandardCopyOption._

import loci.impl.AST.DependencyResolver
import loci.impl.IO._
import loci.impl.main.Containerize

import loci.impl.{Options, Check}
import loci.impl.types.{ModuleConfig, TempLocation}

import sys.process._
import scala.util.Try
//todo always services, never containers?

//todo what happens if nested execpt?

//todo err check script if err ocurred

class Builder(io : IO)(implicit plugin : Containerize){

  def getBuilder(dirs : Map[plugin.TModuleDef, List[TempLocation]], buildDir : File) : build = new build(dirs, buildDir)

  class build(dirs : Map[plugin.TModuleDef, List[TempLocation]], buildDir : File){

    //todo get proper error code

    implicit val logger : Logger = plugin.logger

    private val dependencyResolver : DependencyResolver = new DependencyResolver()

    var libraryPath : Path = _

    private def getRelativeContainerPath(loc : TempLocation): Path = buildDir.toPath.relativize(loc.getTempPath).normalize
    private def byPackageManager(cfg : ModuleConfig, apkSuffix : String = "", aptSuffix : String = "") : String = if(cfg.getJreBaseImage.contains("alpine")) apkSuffix else aptSuffix

    //todo make this a constructor, its first mandat. step
    /**
      * Grasp project dependencies.
      */
    def collectLibraryDependencies(buildDir : Path) : Option[Path] = {
      val tempLibDir = io.createDir(Paths.get(buildDir.toAbsolutePath.toString, Options.libDir)).orNull
      val dependencies : List[Path] = dependencyResolver.classPathDependencies()

      if(Check ? tempLibDir){
        libraryPath = tempLibDir.toPath

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

    /**
      * Build the MANIFEST.MF for the jar files.
      */
    def buildMANIFEST(directory : TempLocation) : Option[File] ={

      val dependencies : List[String] = dependencyResolver.classPathDependencies().map(Options.libraryPathPrefix + _.getFileName) ++ dependencyResolver.classJRELibs()
      val CMD =
        s"""Manifest-Version: 1.0
           |Main-Class: ${ directory.entryPoint.entryClassSymbolString }
           |""" +
          dependencies.foldLeft("Class-Path: ")((C, p) => C + p + " \n ") + "\n"

      io.buildFile(CMD.stripMargin, Paths.get(directory.getTempPathString, "MANIFEST.MF"))
    }
    /**
      * Build the JAR files.
      */
    def buildJARS() : Unit = {
      dirs.flatMap(_._2).foreach(d => {
        val workDir : File = new File(d.getTempUri)
        val manifest = buildMANIFEST(d).orNull
        if(Process("jar -cfm ./" + d.getServiceName + ".jar MANIFEST.MF -C classfiles . ", workDir).!(logger) == 0){
          manifest.delete()
          Try {
            io.recursiveClearDirectory(workDir.listFiles.find(f => f.isDirectory && f.getName == "classfiles").get, self = true)
          }
        }
      })
    }

    /**
      * Build the run scripts for the jars.
      */
    def buildCMDExec() : Unit = {
      dirs.flatMap(_._2).foreach(d => {
        val CMD =
            //deprecated: using iptables: @foreach port =>  s"iptables -t nat -I PREROUTING -p tcp --dport $port -j DNAT --to 127.0.0.1:$port"
            //"mongod --bind_ip_all --dbpath /data/db &\n" +
            "java -jar -Dfile.encoding=UTF-8 " + d.getServiceName + ".jar"
        io.buildFile(io.buildScript(CMD), Paths.get(d.getTempPathString, s"run.sh"))
      })
    }

    /**
      * Build the Dockerfiles for the peer images.
      */
    def buildDockerFiles() : Unit = {
      if(libraryPath == null)
        logger.error("Something went wrong when trying to collect the dependencies: Library path not found.")

      buildDockerBaseFiles()

      dirs.foreach{ mod =>
        mod._2.foreach(d => {
          val cfg = d.entryPoint.config
          val ports : Set[Int] = d.entryPoint.endPoints.filter(_.way != "connect").map(_.port).union(cfg.getPorts).toSet
          val script = cfg.getScript

          val CMD =
            s"""FROM ${ Options.libraryBaseImageTag }
               |LABEL ${Options.labelPrefix}.module: "${mod._1.moduleName}"
               |LABEL ${Options.labelPrefix}.description: "${cfg.getDescription}"
               |WORKDIR ${ Options.containerHome }
               |COPY ./run.sh ./*.jar ./
               |""" +
              (if(d.entryPoint.isGateway && ports.nonEmpty) ports.foldLeft("EXPOSE")((S, port) => S + " " + port) else "") + "\n" +
              Check ?=> (script.orNull,  //todo can we just run it without copy? layers!
                s"COPY ./preRunSpecific.sh ${Options.containerHome}/preRunSpecific.sh \n" +
                  s"RUN ${Options.containerHome}/preRunSpecific.sh \n", "") +
              s"ENTRYPOINT ./run.sh \n"
          //"COPY [\"" + libraryPath.toString.replace("\\", "/") + "\",\"" + Options.unixLibraryPathPrefix + "\"]\r\n" +

          //todo ok?
          if(script.isDefined)
            Files.copy(script.get.toPath, Paths.get(d.getTempPathString, s"preRunSpecific.sh"), REPLACE_EXISTING)

          io.buildFile(CMD.stripMargin, Paths.get(d.getTempPathString, "Dockerfile"))
        })
      }
    }

    /**
      * Build the Dockerfiles for the base images of the peer images (that contain among other things the library dependencies).
      */
    def buildDockerBaseFiles() : Unit = {//todo copy only libs
      dirs.keys.foreach(m => {
        val cfg : ModuleConfig = m.config
        val script : Option[File] = cfg.getScript
        val CMD = //todo COPY --from=0 / / ? or whatever, hauptsache multiple builds work
        //${ Options.dbBaseImage match{ case Some(db) => s"|COPY --from=$db / /" case None => "" } }
          s"""FROM ${cfg.getJreBaseImage} AS jre-build
           ${ cfg.getCustomBaseImage match{ case Some(custom) => s"|COPY --from=$custom / /" case None => "" } }
           |ENV SCALA_VERSION=${ util.Properties.versionNumberString }
           |WORKDIR /
           |RUN ${ byPackageManager(cfg, "apk", "apt-get") } update && ${ byPackageManager(cfg, "apk add iputils", "apt-get install -y apt-utils net-tools iputils-ping") } curl vim iptables procps
           |RUN ${ byPackageManager(cfg, "addgroup --system", "groupadd -r") } ${cfg.getAppName} && ${ byPackageManager(cfg, "adduser -D --system", "useradd --no-log-init -r -g") } ${cfg.getAppName} ${cfg.getAppName}
           |RUN mkdir -p ${Options.containerHome} && mkdir -p ${Options.libraryPathPrefix} && chown ${cfg.getAppName}:${cfg.getAppName} ${Options.containerHome} && chown ${cfg.getAppName}:${cfg.getAppName} ${Options.libraryPathPrefix}
           |USER ${cfg.getAppName}:${cfg.getAppName}
           |COPY . ${Options.libraryPathPrefix}
           |""" + //todo make apts as external list
            Check ?=>[String] (script.orNull,
              s"COPY ./preRun.sh ${Options.libraryPathPrefix}preRun.sh \n" +  //todo can we just run it without copy? layers!
                s"RUN ${ Options.libraryPathPrefix }preRun.sh \n", "")

        if(script.isDefined)
          Files.copy(script.get.toPath, Paths.get(libraryPath.toString, s"preRun.sh"), REPLACE_EXISTING)

        io.buildFile(CMD.stripMargin, Paths.get(libraryPath.toString, s"Dockerfile_${ m.moduleName }"))
      })
    }

    //todo get proper err code, res.: own DNS
    /**
      * Build the peer images.
      */
    def buildDockerImages() : Unit = {
      dirs.foreach{ m =>
        Process(s"bash BuildBaseImage_${ m._1.moduleName }.sh", libraryPath.toFile).!!(logger)

        m._2.foreach{ d =>
          Process("bash " + "BuildContainer.sh" + "\"", d.getTempFile).!!(logger)  //todo cmd is win, but not working without...? + cant get err stream because indirect
        }
      }
    }

    /**
      * Build scripts to build the base images of the peer images.
      */
    def buildDockerBaseImageBuildScripts() : Unit = {
      dirs.keys.foreach{ m =>
        val CMD =
          s"docker build -t ${ Options.libraryBaseImageTag } -f Dockerfile_${ m.moduleName } . \n"
        io.buildFile(io.buildScript(CMD), Paths.get(libraryPath.toAbsolutePath.toString, s"BuildBaseImage_${ m.moduleName }.sh"))
      }
    }
    /**
      * Build scripts to build the peer images.
      */
    def buildDockerImageBuildScripts() : Unit = {
      dirs.foreach { m =>
        m._2.foreach{ d =>
          val peerDir: File = new File(getRelativeContainerPath(d).toString) //todo check if same begin
          val dirDepth: String = "../" * (peerDir.getPath.count(c => c == '/' || c == '\\') + 1)
          val CMD =
            s"cd $dirDepth \n" +
              s"docker build -t ${d.getImageName} -f ${Options.toolbox.toUnixString(peerDir.toPath) + s"/Dockerfile" + " " + Options.toolbox.toUnixString(peerDir.toPath) + "/"} \n"
          io.buildFile(io.buildScript(CMD), Paths.get(d.getTempPathString, s"BuildContainer.sh"))
        }
      }
    }
    /**
      * Build scripts to run the containers (as standalone).
      */
    def buildDockerRunScripts() : Unit = {
      //todo we run containers => jedes mal wird neuer container created, danach nur gestoppt, existiert dann aber weiter => existing containers block creation with same name, also: possibly update instead of recreate?
      dirs.keys.map(_.config.getAppName).toSet[String].foreach{ appName =>
        val globalNet = new Network(plugin.io)(appName, buildDir.toPath)
        globalNet.buildSetupScript()
        //globalNet.buildNetwork() todo: runs network enable scripts, but throws no swarm error bec too early.
      }
      dirs.keys.foreach{ module =>
        val moduleNet = new Network(plugin.io)(module.moduleName, buildDir.toPath)

        dirs.getOrElse(module, List()).foreach{ loc =>

          val useDb = loc.entryPoint.config.getLocalDb.isDefined
          val dbName = if(useDb) loc.getImageName + "_localdb" else ""
//todo network switch for global net on off
          val CMDStart = //todo -v flag ok? removes volume associated //todo -a flag?
            s"""docker rm --volumes -f ${ loc.getImageName }
               |docker volume create ${ loc.getImageName }""" +
              (if(useDb) {//todo env vars user pw
                s"""docker volume create $dbName
                 |docker network create --attachable -d overlay ${loc.getImageName}
                 |docker run -d --name $dbName --network ${loc.getImageName} --volume $dbName:/data -t ${ loc.entryPoint.config.getLocalDb }"""
              } else "") +
            s"""|docker run -id ${ if(loc.entryPoint.isGateway) loc.entryPoint.endPoints.filter(_.way != "connect").map(_.port).toSet.foldLeft("")((S, port) => S + s"--publish ${ port }:${ port } ") else "" } --name ${ loc.getImageName } --network=${loc.getImageName} --volume ${ loc.getImageName }:${ module.config.getContainerVolumeStorage } --cap-add=NET_ADMIN --cap-add=NET_RAW --sysctl net.ipv4.conf.eth0.route_localnet=1 -t ${ loc.getImageName }
                |docker network connect --alias ${ loc.getImageName } ${ moduleNet.getName } ${ loc.getImageName }""" +
              (if(useDb) s"docker network connect --alias ${ loc.getImageName } ${ dbName } ${ loc.getImageName }\n" else "") +
            s"""|docker container inspect -f \"Container '""".stripMargin + loc.getImageName + "' connected to " + module.config.getAppName + " and " + moduleNet.getName + " with ip={{range .NetworkSettings.Networks}}{{.IPAddress}}{{end}}.\" " + loc.getImageName + "\n"

          val CMDStop =
            s"""docker network disconnect ${ module.config.getAppName } ${ loc.getImageName }
               |docker network disconnect ${ moduleNet.getName } ${ loc.getImageName }""" +
               (if(useDb){
                 s"""docker network disconnect ${ dbName } ${ loc.getImageName }
                    |docker stop ${ dbName }
                    |docker container rm -f ${ dbName }
                    |""".stripMargin

               } else "") +
            s"""|docker stop ${ loc.getImageName }
                |docker container rm -f ${ loc.getImageName }""".stripMargin

          io.buildFile(io.buildScript(CMDStart), Paths.get(loc.getTempPathString, s"RunContainer.sh"))//todo add -p, .sh
          io.buildFile(io.buildScript(CMDStop), Paths.get(loc.getTempPathString, s"StopContainer.sh"))
        }
        moduleNet.buildSetupScript()
        //moduleNet.buildNetwork() todo: runs network enable scripts, but throws no swarm error bec too early.
      }
    }
    /**
      * Build global database for modules, if applicable.
      */
    def buildGlobalDatabase(modules : List[(Path, plugin.TModuleDef)]) : Unit = { //todo cred secrets, env
      modules.filter(_._2.config.getGlobalDb.isDefined).foreach { mod =>
        val cfg = mod._2.config
        val db = cfg.getGlobalDb.get
        val moduleName = Options.toolbox.getNameDenominator(mod._2.moduleName)
        val dbName = moduleName + "_globaldb"
        val CMD = s"""docker volume create $dbName
          |docker run -d --name $dbName --network ${ moduleName } --volume $dbName:/data -t ${ db }
          |""" //todo env vars user pw
        plugin.runner.dockerPull(db)
        io.buildFile(io.buildScript(CMD.stripMargin), mod._1.resolve(s"startGlobalDb.sh"))
      }
    }
    /**
      * Pull the newly created images to a repository (default DockerHub).
      */
    def publishDockerImagesToRepo() : Unit = {
      dirs.flatMap(_._2).foreach{ d =>
        val imageTag = s"${ Options.dockerUsername }/${ Options.dockerRepository.toLowerCase }:${ d.getImageName }"
        (Process(s"docker tag ${ d.getImageName } $imageTag") #&& Process(s"docker push $imageTag")).!(logger)
      }
    }
    /**
      * Save the newly created images to disk, if set.
      */
    def saveImageBackups(): Unit = {
      io.createDir(Paths.get(plugin.homeDir.getPath, Options.backupDir))
      dirs.flatMap(_._2).foreach{ d =>
          Process(s"docker save -o ${ Paths.get(Options.backupDir, d.getImageName) }.tar ${ d.getImageName }", plugin.homeDir).!(logger)
      }
    }
  }
}
