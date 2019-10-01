package loci.containerize.container

import java.io.File
import java.nio.file.{Files, Path, Paths}

import loci.containerize.{Check, Options}
import loci.containerize.IO._
import loci.containerize.main.Containerize
import loci.containerize.types.{ContainerConfig, ModuleConfig, TempLocation}

import sys.process._

class Compose(io : IO)(buildDir : File)(implicit plugin : Containerize) {

  def getComposer : compose = new compose()

  class compose(){
    val logger : Logger = plugin.logger
    var composePath : File = _
    val filesPath : String = "files"

    io.createDir(Paths.get(buildDir.getAbsolutePath, Options.composeDir)) match{
      case Some(f) => composePath = f
      case None => logger.error(s"Could not create composer build directory at: ${ buildDir.getAbsolutePath + "/" + Options.composeDir }")
    }
//todo all fun params into constructor
    //todo interestings:
    // - see constraints and prefs for placement (e.g. user defined sec level
    // - extra_hosts
    // - health_check, also in DOCKERRFILE
    // - logging
    // - ip, aliases for versions or something?
    // - ports long syntax
    // - secrets (...?)
    // - --endpoint-mode for custom load balance-...?!?!?
    // - VOLUMES!
    // - secrts for mongo, local + glboal
    def buildDockerCompose(multiTierModule : plugin.TModuleDef, dirs : List[TempLocation]) : Unit = {
      dirs.foreach(l => logger.info("locs: ++ " + l))
      val moduleCfg : ModuleConfig = multiTierModule.config
      val moduleNetworkName = Options.toolbox.getNameDenominator(multiTierModule.moduleName)
      val globalDbCreds : Option[(String,String)] = moduleCfg.getGlobalDbCredentials
      val moduleSecrets : Map[String, String] = moduleCfg.getSecrets.groupBy(_._1).map(_._2.head) //dirs.map(_.entryPoint.config.getSecrets).reduce(_ ++ _).groupBy(_._1).toSet.map(_._2.head)
      val CMD =
        "version: \"3.7\"\n" +
          dirs.foldLeft("services:\n"){ (S, d) =>
            val cfg : ContainerConfig = d.entryPoint.config
            val ports = d.entryPoint.endPoints.filter(_.way != "connect").map(_.port).union(cfg.getPorts).toSet
            val localDbCreds : Option[(String,String)] = cfg.getLocalDbCredentials
            val serviceSecrets : Set[String] = cfg.getSecrets
            val name = d.getServiceName
            S +
              s"""  $name:
              |    # configuration for $name
              |    image: ${ if(Options.published) d.getRepoImageName else d.getImageName }
              |    deploy:
              |      mode: ${ cfg.getDeployMode }
              |      replicas: ${ cfg.getReplicas }
              |      resources:
              |        limits:
              |          cpus: "${ cfg.getCPULimit }"
              |          memory: ${ cfg.getMemLimit }
              |        reservations:
              |          cpus: "${ cfg.getCPUReserve }"
              |          memory: ${ cfg.getMemReserve }
              |      restart_policy:
              |        condition: any
              |      rollback_config:
              |        order: start-first
              |      update_config:
              |        parallelism: 2
              |        failure_action: rollback
              |        order: start-first
              |    volumes:
              |      - type: volume
              |        source: ${ d.getImageName }
              |        target: ${ moduleCfg.getContainerVolumeStorage }
              |""".stripMargin +
              (if(serviceSecrets.nonEmpty) {
                if(!serviceSecrets.forall(s => moduleSecrets.exists(_._1 == s))) logger.error(s"Service ${ d.getServiceName } tries to use a secret that doesn't exist. You must provide all secrets in the form (key, filepath) to the secrets option of your module config before you can refer to them in a service config.")
                s"    ${ serviceSecrets.foldLeft("secrets:\n")((S, secret) => S + "      - " + secret + "\n") }"
              } else "") +
              (if(d.entryPoint.isGateway && ports.nonEmpty)
                s"    ${ ports.foldLeft("ports:\n")((S, port) => S + "      - \"" + port + ":" + port + "\"\n") }"
              else "") +
              s"    networks:" +
              (if(cfg.getNetworkMode == "default") {
                s"""
              |      ${Options.swarmName}:
              |        aliases:
              |          - ${ d.getImageName }
              |"""
                } else "") +
                s"""      $moduleNetworkName:
              |        aliases:
              |          - ${ d.getImageName }\n""" +
              (if(cfg.getLocalDb.isDefined){
                s"""      ${ d.getImageName }:
                   |  ${ name + "_localdb" }:
                   |    # configuration for the local database of $name
                   |    image: ${ cfg.getLocalDb.get }
                   |    deploy:
                   |      replicas: 1
                   |      restart_policy:
                   |        condition: any
                   |    networks:
                   |      ${ d.getImageName }:
                   |        aliases:
                   |          - ${ d.getImageName + "_localdb" }
                   |""".stripMargin + //todo check volume should be generated automatically by db
                  (if(localDbCreds.isDefined && cfg.getLocalDbIdentifier.get == "mongo")
                s"""    environment:
                   |      MONGO_INITDB_ROOT_USERNAME: ${ localDbCreds.get._1 }
                   |      MONGO_INITDB_ROOT_PASSWORD: ${ localDbCreds.get._2 }
                   |""".stripMargin else "")
              } else "") +
              s"""    labels:
                 |      ${Options.labelPrefix}.module: "${multiTierModule.moduleName}"
                 |      ${Options.labelPrefix}.description: "${cfg.getDescription}"
                 |      ${cfg.getServiceMetadata(d)}
                 |""".stripMargin
          } + (if(moduleCfg.getGlobalDb.isDefined){
            s"""  ${ "globaldb" }:
             |    image: ${ moduleCfg.getGlobalDb.get }
             |    deploy:
             |      replicas: 1
             |      restart_policy:
             |        condition: any
             |    networks:
             |      $moduleNetworkName:
             |        aliases:
             |          - ${ moduleNetworkName + "_globaldb" }
             |""".stripMargin +
            (if(globalDbCreds.isDefined && moduleCfg.getGlobalDbIdentifier.get == "mongo")
            s"""    environment:
               |      MONGO_INITDB_ROOT_USERNAME: ${ globalDbCreds.get._1 }
               |      MONGO_INITDB_ROOT_PASSWORD: ${ globalDbCreds.get._2 }
               |""".stripMargin else "")
          } else "") +
          s"""networks:
          |  ${Options.swarmName}:
          |    external: true
          |  $moduleNetworkName:
          |    driver: overlay
          |    attachable: true
          |    internal: false
          |    name: $moduleNetworkName
          |""" + //todo not expect global network if completely shutdown for this module
            dirs.filter(_.entryPoint.config.getLocalDb.isDefined).foldLeft("")((S, d) => {
              S +
                s"""  ${ d.getImageName }:
                   |    driver: overlay
                   |    internal: true
                   |    attachable: false
                   |    name: ${ d.getImageName }
                   |""".stripMargin
            }) +
            dirs.foldLeft("volumes:\n")((S, d) => {
              S + "  " + d.getImageName + ":\n"
            }) +
            moduleSecrets.foldLeft("secrets:\n")((S, d) => {
              io.checkFile(d._2)
              S + s"""  ${d._1}:
                   |    file: "${StringContext.processEscapes(d._2)}"
                   |""".stripMargin
            })

      /**
       * monitor_service:
       * # configuration for monitoring service, running on each master node.
       * image: alexellis2/visualizer-arm:latest
       * deploy:
       * mode: global
       * placement:
       * constraints: [node.role == manager]
       * ports:
       *       - "8080:8080"
       * volumes:
       *       - type: bind
       * source: /var/run/docker.sock
       * target: /var/run/docker.sock
       */
              /**
              |    healthcheck: //todo in dockerfile?
              |      test: [\"CMD\", \"curl\", \"-f\", \"127.0.0.1\"]
              |      interval: 2m
              |      timeout: 15s
              |      retries: 3
              |      start_period: 1m
               ***/
      /**
          "sysctl:\n" +
          " net.ipv4.conf.eth0.route_localnet:1\n" +
          "cap_add:\n" +
          " - NET_ADMIN\n" +
          " - NET_RAW\n"
        */

      io.buildFile(CMD.stripMargin, Paths.get(composePath.getAbsolutePath, filesPath, multiTierModule.moduleName + ".yml"))
    }
    def buildDockerSwarm(multiTierModules : List[String]) : Unit = {
      val CMD = //todo grep leader necess?
        s"""SKIP_NET_INIT=0
           |(docker node ls | grep "Leader") ${Options.errout}
           |if [ $$? -ne 0 ]; then
           |  docker swarm init
           |fi
           |docker network inspect ${Options.swarmName} ${Options.errout}
           |if [ $$? -eq 0 ]; then
           |  docker network rm ${Options.swarmName} ${Options.errout}
           |  if [ $$? -ne 0 ]; then
           |    echo "Could not remove network ${Options.swarmName}. Continuing with the old network. Remove network manually to update it next time."
           |    SKIP_NET_INIT=1
           |  fi
           |fi
           |if [ $$SKIP_NET_INIT -eq 0 ]; then
           |  docker network create -d overlay --attachable=true ${Options.swarmName}
           |fi
           |echo "---------------------------------------------"
           |echo ">>> Creating stacks from compose files... <<<"
           |echo "---------------------------------------------"
           |""" +
            multiTierModules.foldLeft("")((M, m) => M + {
              s"""bash stack-$m.sh
                 |if [ $$? -ne 0 ]; then
                 |  exit 1;
                 |fi
                 |""".stripMargin
            }) +   //${Options.swarmName}
        /*s"""docker service create --publish 8080:8080 --mode global --constraint 'node.role == manager' --mount type=bind,source=/var/run/docker.sock,destination=/var/run/docker.sock --name monitor_service alexellis2/visualizer-arm:latest
           |docker service inspect ${Options.swarmName}_monitor_service ${Options.errout}
           |if [ $$? -eq 0 ]; then
           |  echo "----------------------------------------------------------------------------------"
           |  echo ">>> Swarm Visualizer running on each master node, reachable at: localhost:8080 <<<"
           |  echo "----------------------------------------------------------------------------------"
           |fi
           |"""*/
      s"""|echo "-----------------------"
           |echo ">>> Nodes in Swarm: <<<"
           |echo "-----------------------"
           |docker node ls
           |docker swarm join-token manager
           |docker swarm join-token worker
           |echo "------------------------"
           |echo ">>> Stacks in Swarm: <<<"
           |echo "------------------------"
           |docker stack ls
           |echo "------------------------"
           |""" +
          multiTierModules.foldLeft("")((M, m) => M + {
            s"""|echo "-----------------"
                |echo "Services in stack '${Options.toolbox.getNameDenominator(m)}':"
                |docker stack services ${Options.toolbox.getNameDenominator(m)}
                |""".stripMargin
          }) +
          """|
           |echo "----------------------------------"
           |echo ">>> All services in the Swarm: <<<"
           |echo "----------------------------------"
           |docker service ls
           |echo "--------------------------"
           |echo ">> PRESS ANY KEY TO CONTINUE / CLOSE <<"
           |read -n 1 -s
           |exit 0
           |"""

      io.buildFile(io.buildScript(CMD.stripMargin), Paths.get(composePath.getAbsolutePath, "swarm-init.sh"))
    }

    def buildDockerSwarmStop(multiTierModules : List[String]) : Unit = {
      val CMD =
        multiTierModules.foldLeft("")((M, m) => M + {
          s"""docker stack rm $m
             |docker network rm $m
             |""".stripMargin
        }) +
        s"""docker network rm ${Options.swarmName}
             |docker swarm leave -f
             |""".stripMargin

      io.buildFile(io.buildScript(CMD.stripMargin), Paths.get(composePath.getAbsolutePath, "swarm-stop.sh"))
    }

    def buildDockerStack(multiTierModules : List[(plugin.TModuleDef, List[TempLocation])]) : Unit = {
      multiTierModules.foreach{ m =>
        val moduleName = m._1.moduleName
        val moduleNetwork = plugin.toolbox.getNameDenominator(moduleName)
        val CMD = //todo grep leader necess?
          s"""(docker node ls --filter "role=manager" --format "{{.Self}}" | grep "true") ${Options.errout}
             |if [ $$? -ne 0 ]; then
             |  echo "It appears that this node is not a Swarm Manager. You can only deploy a Stack to a Swarm from one of its manager nodes (use swarm-init.sh, docker swarm init or docker swarm join)."
             |  exit 1
             |fi
             |docker network rm $moduleNetwork ${Options.errout}
             |if [ $$? -eq 0 ]; then
             |  echo "Network $moduleNetwork removed."
             |fi
             |ERR=0
             |""".stripMargin +
              m._2.foldLeft("")((M,s) => {
                M +
                  s"docker images -q ${ s.getAppropriateImageName } ${Options.errout}\n" +
                  "if [ $? -ne 0 ]; then\n" +
                  "  ERR=1\n" +
                  "  echo \"Could not find image " + s.getAppropriateImageName + ", which is needed by service " + s.getServiceName + ". This service will not start up.\"\n" +
                  "fi\n"
              }) +
          s"""if [ $$ERR -ne 0 ]; then
             |  echo "Error: At least one service did not found its base image. Remember that you need to publish images to a repository (enable publishing option) in order to deploy a Swarm on multiple nodes."
             |fi
             |docker stack deploy -c $filesPath/$moduleName.yml $moduleNetwork
             |if [ $$? -eq 0 ]; then
             |  echo "Successfully deployed stack '$moduleNetwork'."
             |  else
             |    echo "Error while deploying stack '$moduleNetwork', aborting now. Please fix before retrying."
             |    exit 1
             |fi
             |""".stripMargin
        io.buildFile(io.buildScript(CMD), Paths.get(composePath.getAbsolutePath, s"stack-$moduleName.sh"))
      }
    }
    def buildTroubleshootScripts(multiTierModules : List[(plugin.TModuleDef, List[TempLocation])]) : Unit = {
      multiTierModules.foreach { m =>
        val CMD =
          m._2.foldLeft(s"""echo "Status for stack ${ m._1.moduleName }:"\n""")((S, d) => {
            S + //todo all this name stuff is mixed up, make it clean
            s"""echo "--------------------------------------------------------------------------"
               |echo "Status of service ${ d.getServiceName }:"
                 |echo "----------------- start of service error output --------------------------"
                 |ID="$$(docker service ps -f "desired-state=running" --format "{{.ID}}" ${ Options.toolbox.getNameDenominator(m._1.moduleName) + "_" + d.getServiceName })"
                 |if [ -z "$${ID}" ]; then
                 |  echo "Service not running."
                 |  else
                 |    OUT="$$(docker service logs "$$ID") $$(docker service ps --no-trunc --format "{{.Error}}" -f "id=$$ID")"
                 |    ([ "$${#OUT}" -lt 2 ] || echo "Everything ok.") &&  echo "$$OUT"
                 |fi
                 |echo "----------------- end of service error output --------------------------"
                 |""".stripMargin
          })
        io.buildFile(io.buildScript(CMD), Paths.get(composePath.getAbsolutePath, s"check-${ m._1.moduleName }.sh"))
      }
    }
    def runDockerSwarm() : Unit = {
      Process("bash swarm-init.sh", composePath).!!(logger.strong) //todo really make this blocking?
      //orig call: cmd /k start bash swarm-init.sh
    }
    //$ docker service rm my-nginx
    //$ docker network rm nginx-net nginx-net-2
  }
}
