package loci.containerize.container

import java.io.File
import java.nio.file.{Path, Paths}

import loci.containerize.Options
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
          "services:\n" +
          "  web:\n" +
          "    # replace username/repo:tag with your name and image details\n" +
          "    image: sasye93/plugin:interactive.timeservicesimple.thatserver\n" +
          "    deploy:\n" +
          "      replicas: 3\n" +
          "      resources:\n" +
          "        limits:\n" +
          "          cpus: \"0.05\"\n" +
          "          memory: 10M\n" +
          "      restart_policy:\n" +
          "        condition: on-failure\n" +
          "    ports:\n" +
          "      - \"43055:43055\"\n" +
          "    networks:\n" +
          "      - webnet\n" +
          "networks:\n" +
          "  webnet:\n" +
          "sysctl:\n" +
          " net.ipv4.conf.eth0.route_localnet:1\n" +
          "cap_add:\n" +
          " - NET_ADMIN\n" +
          " - NET_RAW\n"

      io.buildFile(CMD, Paths.get(composePath.getAbsolutePath, "docker-compose.yml"))
    }
  }
}
