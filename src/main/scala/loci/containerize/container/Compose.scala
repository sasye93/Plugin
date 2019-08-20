package loci.containerize.container

import java.io.File
import java.nio.file.{Path, Paths}

import loci.containerize.{Check, Options}
import loci.containerize.IO._
import loci.containerize.main.Containerize
import loci.containerize.types.TempLocation
import sys.process._

class Compose[+C <: Containerize](io : IO)(implicit plugin : C) {

  def getComposer(dirs : List[TempLocation], buildDir : File) : compose = new compose(dirs, buildDir)

  class compose(dirs : List[TempLocation], buildDir : File){
    val logger : Logger = plugin.logger
    var composePath : File = _

    io.createDir(Paths.get(buildDir.getAbsolutePath, Options.composeDir)) match{
      case Some(f) => composePath = f
      case None => logger.error(s"Could not create composer build directory at: ${ buildDir.getAbsolutePath + "/" + Options.composeDir }")
    }

    //todo interestings:
    // - see constraints and prefs for placement (e.g. user defined sec level
    // - extra_hosts
    // - health_check, also in DOCKERRFILE
    // - logging
    // - ip, aliases for versions or something?
    // - ports long syntax
    // - secrets (...?)
    // - VOLUMES!
    def buildDockerCompose() : Unit = {
      val CMD =
        "version: \"3.7\"\n" +
          dirs.foldLeft("services:\n"){ (s, d) =>
            val cfg : ContainerConfig[C] = new ContainerConfig[C](d.entryPoint.config)(io, plugin)

            s +
             s"  ${ d.getImageName }:\n" +
              s"    # configuration for ${ d.getImageName } (${ cfg.getConfigType }) \n" +
             s"    image: ${ Options.dockerUsername }/${ Options.dockerRepository.toLowerCase }:${ d.getImageName }\n" +
              "    deploy:\n" +
             s"      mode: ${ cfg.getDeployMode }\n" +
             s"      replicas: ${ cfg.getReplicas }\n" +
              "      resources:\n" +
              "        limits:\n" +
              "          cpus: \"" + cfg.getCPULimit + "\"\n" +
             s"          memory: ${ cfg.getMemLimit }\n" +
              "        reservations:\n" +
              "          cpus: \"" + cfg.getCPUReserve + "\"\n" +
             s"          memory: ${ cfg.getMemReserve }\n" +
              "      restart_policy:\n" +
              "        condition: any\n" +
              "      rollback_config:\n" +
              "        order: start-first\n" +
              "      update_config:\n" +
              "        parallelism: 2\n" +
              "        failure_action: rollback\n" +
              "        order: start-first\n" +
            /**
              "    healthcheck:\n" + //todo in dockerfile?
              "      test: [\"CMD\", \"curl\", \"-f\", \"127.0.0.1\"]\n" +
              "      interval: 2m\n" +
              "      timeout: 15s\n" +
              "      retries: 3\n" +
              "      start_period: 1m\n" +
             */
              "    labels:\n" +
             s"      ${ Options.labelPrefix }.service: " + "\"service\"\n" +
              (if(d.entryPoint.endPoints.exists(_.way != "connect"))
             s"    ${ d.entryPoint.endPoints.foldLeft("ports:\n")((s, e) => if(e.way == "connect" && Check ? e.port) s else s + "      - \"" + e.port + ":" + e.port + "\"\n") }"
              else "") +
              "    networks:\n" +
              "      mynet:\n" +
              "        aliases:\n" +
             s"          - ${ d.getImageName }\n"
          } +
          "networks:\n" +
          "  mynet:\n" +
          "    driver: overlay\n" +
          "    attachable: true\n"
      /**
          "sysctl:\n" +
          " net.ipv4.conf.eth0.route_localnet:1\n" +
          "cap_add:\n" +
          " - NET_ADMIN\n" +
          " - NET_RAW\n"
        */

      io.buildFile(CMD, Paths.get(composePath.getAbsolutePath, "docker-compose.yml"))
    }
    def runDockerSwarm() : Unit = {
      Process("cmd /k start bash swarm-init.sh", composePath).!!(logger.strong) //todo really make this blocking?
    }
    //$ docker service rm my-nginx
    //$ docker network rm nginx-net nginx-net-2

    def buildDockerSwarm() : Unit = {
      val CMD = {
        "docker swarm init\n" +
        "if [ $? -ne 0 ]; then\n" +
        //"   echo \"$?\"\n" +
        "   exit 1\n" +
        "else\n" +
        s"   docker stack deploy -c docker-compose.yml ${ Options.swarmName /*todo */ }\n" +
        "   if [ $? -eq 0 ]; then\n" +
          "     echo \"-----------------------\"\n" +
          "     echo \"--- Nodes in Swarm: ---\"\n" +
          "     echo \"-----------------------\"\n" +
          "     docker node ls\n" +
          "     docker swarm join-token manager\n" +
          "     docker swarm join-token worker\n" +
          "     echo \"--------------------------\"\n" +
          "     echo \"--- Services in Swarm: ---\"\n" +
          "     echo \"--------------------------\"\n" +
          "     docker service ls\n" +
          "     echo \"--------------------------\"\n" +
          "     echo \">> PRESS ANY KEY TO CONTINUE / CLOSE <<\"\n" +
          "     read -n 1 -s\n" +
          "     exit 0 \n" +
          "   else\n" +
          "     exit 1\n" +
          " fi\n" +
        "fi\n"
      }

      io.buildFile(CMD, Paths.get(composePath.getAbsolutePath, "swarm-init.sh"))
    }

  }
}
