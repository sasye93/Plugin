/**
  * Compose class, builds all compose/swarm related files.
  * @author Simon Schönwälder
  * @version 1.0
  */
package loci.container.build.images

import java.io.File
import java.nio.file.Paths

import loci.container.build.Options
import loci.container.build.IO._
import loci.container.build.main.Containerize
import loci.container.build.types.{ServiceConfig, ModuleConfig, TempLocation}

import sys.process._

class Compose(io : IO)(buildDir : File)(implicit plugin : Containerize) {

  def getComposer : compose = new compose()

  class compose(){
    implicit val logger : Logger = plugin.logger
    var composePath : File = _
    val filesPath : String = "files"

    io.createDir(Paths.get(buildDir.getAbsolutePath, Options.composeDir)) match{
      case Some(f) => composePath = f
      case None => logger.error(s"Could not create composer build directory at: ${ buildDir.getAbsolutePath + "/" + Options.composeDir }")
    }
    /**
      * Build the compose/swarm yml files, one yml per @containerize. This file can be used to start the whole stack and
      * all services in it. To do so, use the respective stack-XXX.sh script (or manually using docker stack <...>).
      */
    //todo interestings:
    // - see constraints and prefs for placement (e.g. user defined sec level)
    // - health_check, also in DOCKERRFILE
    // - logging
    // - ip, aliases for versions or something
    def buildDockerCompose(multiTierModule : plugin.TModuleDef, dirs : List[TempLocation]) : Unit = {
      val moduleCfg : ModuleConfig = multiTierModule.config
      val moduleNetworkName = Options.toolbox.getNameDenominator(multiTierModule.moduleName)
      val globalDbCreds : Option[(String,String)] = moduleCfg.getGlobalDbCredentials
      val moduleSecrets : Map[String, String] = moduleCfg.getSecrets.groupBy(_._1).map(_._2.head) //dirs.map(_.entryPoint.config.getSecrets).reduce(_ ++ _).groupBy(_._1).toSet.map(_._2.head)
      val CMD =
        "version: \"3.7\"\n" +
          dirs.foldLeft("services:\n"){ (S, d) =>
            val cfg : ServiceConfig = d.entryPoint.config
            val ports = d.entryPoint.endPoints.filter(_.way != "connect").map(_.port).union(cfg.getPorts).toSet
            val localDbCreds : Option[(String,String)] = cfg.getLocalDbCredentials
            val serviceSecrets : Set[String] = cfg.getSecrets
            val name = d.getServiceName
            S +
              s"""  $name:
              |    # configuration for $name
              |    image: ${ d.getAppropriateImageName }
              |    stdin_open: ${ cfg.getAttachable }
              |    tty: ${ cfg.getAttachable }
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
              |      ${moduleCfg.getAppName}:
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
                   |    volumes:
                   |      - type: volume
                   |        source: ${ d.getImageName + "_localdb" }
                   |        target: /data/db
                   |""".stripMargin +
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
             |    volumes:
             |      - type: volume
             |        source: ${ moduleNetworkName + "_globaldb" }
             |        target: /data/db
             |""".stripMargin +
            (if(globalDbCreds.isDefined && moduleCfg.getGlobalDbIdentifier.get == "mongo")
            s"""    environment:
               |      MONGO_INITDB_ROOT_USERNAME: ${ globalDbCreds.get._1 }
               |      MONGO_INITDB_ROOT_PASSWORD: ${ globalDbCreds.get._2 }
               |""".stripMargin else "")
          } else "") +
          s"""networks:
          |  ${moduleCfg.getAppName}:
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
            dirs.filter(_.entryPoint.config.getLocalDb.isDefined).foldLeft("")((S, d) => {
              S + "  " + d.getImageName + "_localdb" + ":\n"
            }) +
          (if(moduleCfg.getGlobalDb.isDefined){
            s"  ${ moduleNetworkName + "_globaldb" }:" + "\n"
          } else "") +
          (if(moduleSecrets.nonEmpty)
            moduleSecrets.foldLeft("secrets:\n")((S, d) => {
              val file = io.resolvePath(d._2, moduleCfg.getHome.orNull)
              S + (if(file.isDefined){
                s"""  ${d._1}:
                   |    file: ${file.get.getPath.replaceAll("\\{1,}", "\\\\")}
                   |""".stripMargin
              } else "")
            }) else "")

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
              |    healthcheck:
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
    /**
      * Build the swarm-init.sh script, starting point to start the whole application as swarm.
      */
    def buildDockerSwarm(multiTierModules : List[plugin.TModuleDef]) : Unit = {
      val CMD =
       s"""(docker node ls | grep "Leader") ${Options.errout}
           |if [ $$? -ne 0 ]; then
           |  docker swarm init --advertise-addr eth0
           |fi
           |""".stripMargin +
      multiTierModules.map(_.config.getAppName).toSet.foldLeft("")((N, appName) => {
        N + s"""SKIP_NET_INIT=0
               |docker network inspect ${appName} ${Options.errout}
               |if [ $$? -eq 0 ]; then
               |  docker network rm ${appName} ${Options.errout}
               |  if [ $$? -ne 0 ]; then
               |    echo "Could not remove network ${appName}. Continuing with the old network. Remove network manually to update it next time."
               |    SKIP_NET_INIT=1
               |  fi
               |fi
               |if [ $$SKIP_NET_INIT -eq 0 ]; then
               |  docker network create -d overlay --attachable=true ${appName}
               |fi
               |""".stripMargin
      }) +
       s"""|echo "---------------------------------------------"
           |echo ">>> Creating stacks from compose files... <<<"
           |echo "---------------------------------------------"
           |""".stripMargin +
            multiTierModules.foldLeft("")((M, m) => M + {
              s"""bash stack-${m.moduleName}.sh
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
           |""".stripMargin +
          multiTierModules.foldLeft("")((M, m) => M + {
            s"""|echo "-----------------"
                |echo "Services in stack '${Options.toolbox.getNameDenominator(m.moduleName)}':"
                |docker stack services ${Options.toolbox.getNameDenominator(m.moduleName)}
                |""".stripMargin
          }) +
          """|
           |echo "----------------------------------"
           |echo ">>> All services in the Swarm: <<<"
           |echo "----------------------------------"
           |docker service ls
           |echo "--------------------------"
           |echo "Swarm initialization done. Note that you might have to forward ports to your machine to make them accessable if you run Docker inside a VM (e.g. toolbox)."
           |echo ">> PRESS ANY KEY TO CONTINUE / CLOSE <<"
           |read -n 1 -s
           |exit 0
           |""".stripMargin

      io.buildFile(io.buildScript(CMD), Paths.get(composePath.getAbsolutePath, "swarm-init.sh"))
    }
    /**
      * Build the swarm-stop.sh script, to stop the whole application.
      */
    def buildDockerSwarmStop(multiTierModules : List[plugin.TModuleDef]) : Unit = {
      val CMD =
        multiTierModules.foldLeft("")((M, m) => M + {
          s"""docker stack rm ${m.moduleName}
             |docker network rm ${m.moduleName}
             |""".stripMargin
        }) +
        multiTierModules.map(_.config.getAppName).toSet.foldLeft("")((N, appName) => {
          N + s"docker network rm ${appName}\n"
        }) +
        s"docker swarm leave -f"

      io.buildFile(io.buildScript(CMD.stripMargin), Paths.get(composePath.getAbsolutePath, "swarm-stop.sh"))
    }

    /**
     * Build the stack-XXX.sh scripts that allow to start each microservice domain (each @containerize module and its services) separately.
     * Using this as standalone (not invoking swarm-init.sh) requires to init the swarm priorily (docker swarm init).
     */
    def buildDockerStack(multiTierModules : List[(plugin.TModuleDef, List[TempLocation])]) : Unit = {
      multiTierModules.foreach{ m =>
        val moduleName = m._1.moduleName
        val appName = m._1.config.getAppName
        val moduleNetwork = Options.toolbox.getNameDenominator(moduleName)
        val CMD =
          s"""(docker node ls --filter "role=manager" --format "{{.Self}}" | grep "true") ${Options.errout}
             |if [ $$? -ne 0 ]; then
             |  echo "It appears that this node is not a Swarm Manager. You can only deploy a Stack to a Swarm from one of its manager nodes (use swarm-init.sh, 'docker swarm init' or 'docker swarm join')."
             |  exit 1
             |fi
             |docker network inspect ${appName} ${Options.errout}
             |if [ $$? -ne 0 ]; then
             |  docker network create -d overlay --attachable=true ${appName}
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

    /**
      * Build the check-XXX.sh scripts that facilitate to print error logs of services.
      */
    def buildTroubleshootScripts(multiTierModules : List[(plugin.TModuleDef, List[TempLocation])]) : Unit = {
      multiTierModules.foreach { m =>
        val CMD =
          m._2.foldLeft(s"""echo "Status for stack ${ m._1.moduleName }:"\n""")((S, d) => {
            S + //todo all this name stuff is mixed up, make it clean
            s"""echo "--------------------------------------------------------------------------"
               |echo "Status of service ${ d.getServiceName }:"
                 |echo "----------------- start of service error output --------------------------"
                 |ID="$$(docker service ps --filter "desired-state=running" --format "{{.ID}}" ${ Options.toolbox.getNameDenominator(m._1.moduleName) + "_" + d.getServiceName })"
                 |if [ -z "$${ID}" ]; then
                 |  echo "Service not running."
                 |  else
                 |    OUT="$$(docker service logs --raw --details --timestamps "$$ID") $$(docker ps --no-trunc --format "{{.Error}}" --filter "id=$$ID")"
                 |    ([ "$${#OUT}" -lt 2 ] || echo "Everything ok.") &&  echo "$$OUT"
                 |fi
                 |echo "----------------- end of service error output --------------------------"
                 |""".stripMargin
          }) + "echo \"Note: This script will only show running services, to show the error logs of failed services, use docker service/container ls -a to get the respective ip and then docker service/container logs <ID> and docker ps --filter \'id=<ID>\'\"."
        io.buildFile(io.buildScript(CMD), Paths.get(composePath.getAbsolutePath, s"check-${ m._1.moduleName }.sh"))
      }
    }
    def runDockerSwarm() : Unit = {
      Process("bash swarm-init.sh", composePath).run() //orig: -> cmd /k start bash swarm-init.sh
      //orig call: cmd /k start bash swarm-init.sh
    }
  }
}
