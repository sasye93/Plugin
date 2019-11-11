/**
  * Build phase, where output (images, etc.) is generated. This is the plugin compilers single stage.
  * @author Simon Schönwälder
  * @version 1.0
  */
package loci.container.build.components

import java.util.concurrent.atomic.AtomicBoolean
import java.io._
import java.nio.file.{Path, Paths}

import loci.container.build.main.Containerize
import loci.container.build.types.TempLocation
import loci.container.build.Options
import loci.container.build.images._

import scala.tools.nsc.plugins.PluginComponent
import scala.tools.nsc.Global
import scala.reflect.internal.Phase

class BuildComponent(implicit val plugin : Containerize) extends PluginComponent{

  implicit val global : Global = plugin.global
  implicit val parent : Containerize = plugin

  import plugin._
  import global._

  val component: PluginComponent = this

  override val runsAfter : List[String] = List[String]("jvm")
  override val runsRightAfter: Option[String] = Some("jvm")
  override val phaseName : String = plugin.name

  def newPhase(_prev: Phase) = new BuildPhase(_prev)

  // The actual build phase.
  class BuildPhase(prev: Phase) extends StdPhase(prev) {

    override def name: String = phaseName

    val executed : AtomicBoolean = new AtomicBoolean()

    def apply(unit: CompilationUnit) : Unit ={

      //Only execute this phase once.
      if(executed.compareAndSet(false, true)) {

        //Abort if anything went wrong priorly.
        if(Options.initAbort || !runner.dockerIsRunning()) return

        /**
         * load analyzed peer and module data.
         */
        val tempDir : File = new File(Options.tempDir.toUri)
        if(tempDir == null || !tempDir.exists){
          logger.warning(s"Could not find any module or peer definitions, temporary directory ${ Options.tempDir.toString } doesn't exist.")
          return
        }

        /**
          * Collect the @containerize module definitions pickled and saved previously through the @containerize macro implementation {@link loci.container.containerize}.
          */
        val Modules : TModuleList = dependencyResolver filterDisabled tempDir.listFiles(f => f.getName.endsWith(".mod")).foldLeft(List[TSimpleModuleDef]())((L, mod) => {
          scala.util.Try{ L :+ io.deserialize[TSimpleModuleDef](mod.toPath).get }.getOrElse(L)
        }).map(mod => new TModuleDef(mod))

        //Stop if there are no @containerize modules.
        Options.containerize = Modules.nonEmpty
        if(!Options.containerize) return

        /**
          * Collect the @service/@gateway object definitions pickled and saved previously through the @service/@gateway macro implementation {@link loci.container.service} and {@link loci.container.gateway}.
          */
        var EntryPoints : TEntryList = tempDir.listFiles(f => f.getName.endsWith(".ep")).foldLeft(List[TSimpleEntryDef]())((L, ep) => {
          scala.util.Try{ L :+ io.deserialize[TSimpleEntryDef](ep.toPath).get }.getOrElse(L)
        }).map(ep => new TEntryDef(ep, dependencyResolver.getModuleOfEntryPoint(ep, Modules)))

        var Peers : TPeerList = Modules.foldLeft(List[TPeerDef]())((L, m) => L ++ m.peers)

        //Grasp project dependencies.
        plugin.dependencyResolver.dependencies(EntryPoints, Peers) match{ case (a,b) => EntryPoints = a; Peers = b; }

        io.recursiveClearDirectory(Options.tempDir.toFile, self = true)

        /**
          * Clean prior builds.
          */
        if(Options.cleanBuilds){
          //io.recursiveClearDirectory(plugin.containerDir)
          io.recursiveClearDirectory(plugin.homeDir)
        }

        val peerMappings : collection.mutable.Map[TModuleDef, List[TempLocation]] = collection.mutable.HashMap[TModuleDef, List[TempLocation]]()
        val buildPath = Paths.get(plugin.homeDir.getAbsolutePath, Options.dirPrefix + Options.toolbox.getFormattedDateTimeString)

        //Create the output dir where to store everything.
        io.createDirRecursively(buildPath).getOrElse(logger.error(s"Could not create output directory ${buildPath}. You could try to create the parent directory (${plugin.homeDir.getAbsolutePath}) manually and try again."))

        /**
          * Copy peer files.
          */
        def copy(module : TModuleDef, entryPoint : TEntryDef) : Unit = {
          val s : Path = Paths.get(buildPath.toAbsolutePath.toString, Options.containerDir, module.getLocDenominator, entryPoint.getLocDenominator)
          io.createDirRecursively(s) match{
            case Some(d) =>
              io.copyContentOfDirectory(plugin.outputDir.toPath, Paths.get(d.getAbsolutePath, "classfiles"))
              peerMappings update (module, peerMappings.getOrElse(module, List[TempLocation]()) :+ TempLocation(entryPoint.entryClassSymbolString, d.toPath, entryPoint))
            case None => logger.error(s"Could not create directory: ${ s.toString }")
          }
        }

        Modules.foreach(module => {
          module.peers.foreach(peer =>
            dependencyResolver
              .getAssocEntryPointsOfPeer(EntryPoints, peer)
              .foreach(e => copy(module, e)))
        })

        val builder = new Builder(plugin.io).getBuilder(peerMappings.toMap, buildPath.toFile)
        val composer = new Compose(plugin.io)(buildPath.toFile).getComposer

        /**
          * Start the process.
          */
        logger.info("Collect libraries and dependencies...")
        builder.collectLibraryDependencies(buildPath)
        logger.info("Build JAR files from ScalaLoci peers...")
        builder.buildJARS()
        logger.info("Build run scripts...")
        builder.buildCMDExec()
        logger.info("Build Dockerfiles...")
        builder.buildDockerFiles()

        if(Options.stage >= Options.image){
          logger.info("Build scripts for base image...")
          builder.buildDockerBaseImageBuildScripts()
          logger.info("Build scripts for peer images...")
          builder.buildDockerImageBuildScripts()
          logger.info("Build image run scripts...")
          builder.buildDockerRunScripts()
          logger.info("Build global databases, if applicable...")
          builder.buildGlobalDatabase(Modules.map(mod => (Paths.get(buildPath.toAbsolutePath.toString, Options.containerDir, mod.getLocDenominator), mod)))
          logger.info("Build peer images...")
          builder.buildDockerImages()
          if(Options.saveImages){
            logger.info("Save backups of images (this may take very long)...")
            builder.saveImageBackups()
          }
        }
        if(Options.publishImages) {
          logger.info(s"Pushing images to repository (this may take long depending on your internet connection, pushed to: ${Options.dockerUsername}:${Options.dockerRepository} @ ${if(Options.dockerHost.length > 0) Options.dockerHost else "DockerHub"})...")
          builder.publishDockerImagesToRepo()
        }
        if(Options.stage >= Options.swarm){
          peerMappings.foreach(l => composer.buildDockerCompose(l._1, l._2))
          val stacks = peerMappings.toList.map(_._1)
          logger.info("Build Swarm files and scripts...")
          composer.buildDockerSwarm(stacks)
          composer.buildDockerSwarmStop(stacks)
          composer.buildDockerStack(peerMappings.toList)
          composer.buildTroubleshootScripts(peerMappings.toList)

          if(Options.stage >= Options.run)
            logger.info("Run Swarm...")
            composer.runDockerSwarm()
          //runner.Swarm.deploy("test", Paths.get(outputDir.toString, "docker-compose.yml"))
        }
        //if(Options.stage.id > 2)
          //runner.runContainer(null)

        logger.info(s"Containerization extension finished, deployment path: ${ buildPath.toString }.\nUse ./compose/swarm-init.sh to start the swarm with all built stacks, or stack-XXX.sh to only start a certain stack.")
      }
    }
  }
}