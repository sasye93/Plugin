package loci.containerize.container

import java.io.File
import java.nio.file.{Path, Paths}

import loci.containerize.{Check, Options}
import loci.containerize.IO._
import loci.containerize.main.Containerize
import loci.containerize.types.TempLocation

class Compose[+C <: Containerize](io : IO)(implicit plugin : C) {

  def getComposer(dirs : List[TempLocation], buildDir : File) : compose = new compose(dirs, buildDir)

  class compose(dirs : List[TempLocation], buildDir : File){
    val logger : Logger = plugin.logger
    var composePath : File = _

    io.createDir(Paths.get(buildDir.getAbsolutePath, Options.composeDir)) match{
      case Some(f) => composePath = f
      case None => logger.error(s"Could not create composer build directory at: ${ buildDir.getAbsolutePath + "/" + Options.composeDir }")
    }

    def buildDockerCompose() : Unit = {
      val CMD =
        "version: \"3\"\n" +
          dirs.foldLeft("services:\n"){ (s, d) => s +
             s"  ${ d.getImageName }:\n" +
              "    # replace username/repo:tag with your name and image details\n" +
             s"    image: ${ Options.dockerUsername }/${ Options.dockerRepository.toLowerCase }:${ d.getImageName }\n" +
              "    deploy:\n" +
              "      replicas: 3\n" +
              "      resources:\n" +
              "        limits:\n" +
              "          cpus: \"0.1\"\n" +
              "          memory: 256M\n" +
              "      restart_policy:\n" +
              "        condition: on-failure\n" +
              (if(d.entryPoint.endPoints.exists(_.way != "connect"))
             s"    ${ d.entryPoint.endPoints.foldLeft("ports:\n")((s, e) => if(e.way == "connect" && Check ? e.port) s else s + "      - \"" + e.port + ":" + e.port + "\"\n") }"
              else "") +
              "    networks:\n" +
              "      - mynet\n"
          } +
          "networks:\n" +
          "  mynet:\n"
      /**
          "sysctl:\n" +
          " net.ipv4.conf.eth0.route_localnet:1\n" +
          "cap_add:\n" +
          " - NET_ADMIN\n" +
          " - NET_RAW\n"
        */

      io.buildFile(CMD, Paths.get(composePath.getAbsolutePath, "docker-compose.yml"))
    }
    def runDockerCompose() : Unit = ???
    //$ docker service rm my-nginx
    //$ docker network rm nginx-net nginx-net-2
  }
}
