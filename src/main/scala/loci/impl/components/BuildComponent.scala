/**
  * Build phase, where output (images, etc.) is generated.
  * @author Simon Schönwälder
  * @version 1.0
  */
package loci.impl.components

import java.util.concurrent.atomic.AtomicBoolean
import java.io._
import java.nio.file.{Files, Path, Paths}

import loci.impl.main.Containerize
import loci.impl.types.TempLocation

import scala.tools.nsc.plugins.PluginComponent
import scala.reflect.internal.Phase
import scala.tools.nsc.Global
import loci.impl.Options
import loci.impl.container._

class BuildComponent(implicit val plugin : Containerize) extends PluginComponent{

  implicit val global : Global = plugin.global
  implicit val parent : Containerize = plugin

  import plugin._
  import global._

  val component: PluginComponent = this

  override val runsAfter : List[String] = List[String]("jvm")
  override val runsRightAfter: Option[String] = Some("jvm")
  override val phaseName : String = plugin.name + "-build"

  def newPhase(_prev: Phase) = new BuildPhase(_prev)

  class BuildPhase(prev: Phase) extends StdPhase(prev) {

    override def name: String = phaseName

    val executed : AtomicBoolean = new AtomicBoolean()

    def apply(unit: CompilationUnit) : Unit ={

      if(executed.compareAndSet(false, true)) {

        if(!runner.dockerIsRunning()) return;
        logger.info("Starting build stage...")
        /**
         * load analyzed peer and module data.
         */
        val tempDir : File = new File(Options.tempDir.toUri)
        if(tempDir == null || !tempDir.exists) return;

        val Modules : TModuleList = dependencyResolver filterDisabled tempDir.listFiles((f) => f.getName.endsWith(".mod")).foldLeft(List[TSimpleModuleDef]())((L, mod) => {
          scala.util.Try{ L :+ io.deserialize[TSimpleModuleDef](mod.toPath).get }.getOrElse(L)
        }).map(mod => new TModuleDef(mod))

        Options.containerize = Modules.nonEmpty
        if(!Options.containerize) return;

        var EntryPoints : TEntryList = tempDir.listFiles(f => f.getName.endsWith(".ep")).foldLeft(List[TSimpleEntryDef]())((L, ep) => {
          scala.util.Try{ L :+ io.deserialize[TSimpleEntryDef](ep.toPath).get }.getOrElse(L)
        }).map(ep => new TEntryDef(ep, dependencyResolver.getModuleOfEntryPoint(ep, Modules)))

        var Peers : TPeerList = Modules.foldLeft(List[TPeerDef]())((L, m) => L ++ m.peers)

        plugin.dependencyResolver.dependencies(EntryPoints, Peers) match{ case (a,b) => EntryPoints = a; Peers = b; }

        //io.recursiveClearDirectory(Options.tempDir.toFile, true) todo enable
/*
        def pathify(aClass : TAbstractClassDef) : TAbstractClassDef = {
          val classFile : AbstractFile = global.genBCode.postProcessorFrontendAccess.backendClassPath.findClassFile(aClass.classSymbol.javaClassName).orNull
          if(classFile == null) reporter.warning(null, s"output file for class not found: ${ aClass.classSymbol.javaClassName }")

          aClass.copy(outputPath = Some(global.genBCode.postProcessorFrontendAccess.compilerSettings.outputDirectory(classFile).file), filePath = Some(classFile))
        }
*/
        //PeerDefs = PeerDefs.map(pathify)


        /** todo this is automatic finding by name, include it into the other todo may help fileutils.
        ClassDefs = ClassDefs.map(c => {

          //for containers every container must have one entry point class where only this peer is used. ?
          //right now, this class must have same name as peer
          //todo automate
          val entryPoints = global.genBCode.postProcessorFrontendAccess.getEntryPoints
          if(entryPoints.isEmpty)
            reporter.warning(null, "no entry points found by compiler.")

          EntryPoints.toList.foreach(e => {
            reporter.warning(null, "ENTRY: " + e.toString)
          })

          val entryPoint = entryPoints.find(_ == (c.packageName.toString + "." + c.className.toString)).orNull

          if(entryPoint == null)
            reporter.error(null, "no entry point found for peer " + c.classSymbol.javaClassName)

          //todo pathify of classfiles is not recursive
          pathify(c).copy(
            classFiles = c.classFiles.map(pathify),
            entryPoint = entryPoint
          )
        })
          */

        //reporter.warning(null, global.genBCode.postProcessorFrontendAccess.getEntryPoints.toString)

        if(Options.cleanBuilds){
          //io.recursiveClearDirectory(plugin.containerDir)
          io.recursiveClearDirectory(plugin.homeDir)
        }

        val locs : collection.mutable.Map[TModuleDef, List[TempLocation]] = collection.mutable.HashMap[TModuleDef, List[TempLocation]]()
        val buildPath = Files.createDirectory(Paths.get(plugin.homeDir.getAbsolutePath, Options.dirPrefix + Options.toolbox.getFormattedDateTimeString))

        def copy(module : TModuleDef, entryPoint : TEntryDef) : Unit = {
          val s : Path = Paths.get(buildPath.toAbsolutePath.toString, Options.containerDir, module.getLocDenominator, entryPoint.getLocDenominator)
          io.createDirRecursively(s) match{
            case Some(d) =>
              io.copyContentOfDirectory(plugin.outputDir.toPath, Paths.get(d.getAbsolutePath, "classfiles"))
              locs update (module, locs.getOrElse(module, List[TempLocation]()) :+ TempLocation(entryPoint.entryClassSymbolString, d.toPath, entryPoint))
            case None => logger.error(s"Could not create directory: ${ s.toString }")
          }
          /**
          ClassDefs.foreach(f => {
                //reporter.warning(null, f.filePath.toString)
                //reporter.warning(null, Paths.get(tempSubPath.toString, f.getName).toString)

                if(f.filePath != null){
                  val filePath : Path = Paths.get(f.filePath.path).normalize
                  val filePathName = f.filePath.file.getName
                  val packageSubPath = "/" + f.packageName.replace('.', '/') + "/"
                  //reporter.warning(null, " LK" + Paths.get(tempSubPath.toString, packageSubPath))
                  io.createFolderStructure(Paths.get(tempSubPath.toString, packageSubPath))
                  Files.copy(filePath, Paths.get(tempSubPath.toString, packageSubPath, filePathName.toString), REPLACE_EXISTING)
                }

                /*
                //todo better
                val canonicalName = filePathName.replace("$", "")
                val parentFiles = Paths.get(f.filePath.file.getParent, canonicalName)
                if(Files.exists(parentFiles))
                  Files.copy(parentFiles, Paths.get(tempSubPath.toString, packageSubPath, canonicalName), REPLACE_EXISTING)
           */
              })
           */
          //Files.createFile(Paths.get(tempSubPath.toString, "MANIFEST.MF"))

          //tempSubPath.toFile.deleteOnExit()
        }


        Modules.foreach(module => {
          module.peers.foreach(peer =>
            dependencyResolver
              .getAssocEntryPointsOfPeer(EntryPoints, peer)
              .foreach(e => copy(module, e)))
        })


        val builder = new Builder(plugin.io).getBuilder(locs.toMap, buildPath.toFile)
        val composer = new Compose(plugin.io)(buildPath.toFile).getComposer

        //plugin.classPath.asClassPathString
        logger.info("Collect libraries and dependencies...")
        builder.collectLibraryDependencies(buildPath)
        logger.info("Build JAR files from ScalaLoci peers...")
        builder.buildJARS()
        logger.info("Build run scripts...")
        builder.buildCMDExec()
        logger.info("Build Dockerfiles...")
        builder.buildDockerFiles()
        //builder.createReadme(buildPath)

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

          //runner.runLandscape(locs)
        }
        if(Options.stage >= Options.publish) {
          logger.info(s"Pushing images to repository (this may take long, pushed to: ${Options.dockerUsername}:${Options.dockerRepository} @ ${if(Options.dockerHost.length > 0) Options.dockerHost else "DockerHub"})...")
          builder.publishDockerImagesToRepo()
        }
        if(Options.stage >= Options.swarm){
          locs.foreach(l => composer.buildDockerCompose(l._1, l._2))
          val stacks = locs.toList.map(_._1)
          logger.info("Build Swarm files and scripts...")
          composer.buildDockerSwarm(stacks)
          composer.buildDockerSwarmStop(stacks)
          composer.buildDockerStack(locs.toList)
          composer.buildTroubleshootScripts(locs.toList)

          //todo wie mit runnen?
          //logger.info("Run Swarm...")
          //runner.Swarm.init()
          //compose.runDockerSwarm()
          //runner.Swarm.deploy("test", Paths.get(outputDir.toString, "docker-compose.yml"))
        }
        //if(Options.stage.id > 2)
          //runner.runContainer(null)

        logger.info(s"Containerization plugin finished, deployment path: ${ buildPath.toString }")
      }
    }
  }
}