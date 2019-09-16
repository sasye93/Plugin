package loci.containerize.components

import java.util.concurrent.atomic.AtomicBoolean
import java.io._
import java.nio.file.{Files, Path, Paths}
import java.util.Calendar

import loci.containerize.AST.DependencyResolver
import loci.containerize.IO.IO
import loci.containerize.main.Containerize
import loci.containerize.types.TempLocation

import scala.tools.nsc.plugins.PluginComponent
import scala.reflect.internal.Phase
import scala.reflect.io.AbstractFile
import scala.tools.nsc.Global
import loci.containerize.Options
import loci.containerize.container._

class BuildComponent(implicit val plugin : Containerize) extends PluginComponent{

  implicit val global : Global = plugin.global
  implicit val parent : Containerize = plugin

  import plugin._
  import global._
  //type TAbstractClassDef = AbstractClassDef[plugin.global.Type, plugin.global.TypeName, plugin.global.Symbol]

  val component: PluginComponent = this

  override val runsAfter : List[String] = List[String]("jvm")
  override val runsRightAfter: Option[String] = Some("jvm")
  override val phaseName : String = plugin.name + "-build"

  def newPhase(_prev: Phase) = new BuildPhase(_prev)

  class BuildPhase(prev: Phase) extends StdPhase(prev) {

    override def name: String = phaseName

    val executed : AtomicBoolean = new AtomicBoolean()

    def apply(unit: CompilationUnit) : Unit ={
      var s1 = System.nanoTime()

      if(Options.containerize && executed.compareAndSet(false, true)) {
        reporter.warning(null, "CALL END")

        def pathify(aClass : TAbstractClassDef) : TAbstractClassDef = {
          val classFile : AbstractFile = global.genBCode.postProcessorFrontendAccess.backendClassPath.findClassFile(aClass.classSymbol.javaClassName).orNull
          if(classFile == null) reporter.warning(null, s"output file for class not found: ${ aClass.classSymbol.javaClassName }")

          aClass.copy(outputPath = Some(global.genBCode.postProcessorFrontendAccess.compilerSettings.outputDirectory(classFile).file), filePath = Some(classFile))
        }

        PeerDefs = PeerDefs.map(pathify)


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

        logger.warning("BUsssssssssssssssssssssssssssssssssssssssssss " + plugin.homeDir.listFiles().foldLeft("")((b, s) => b + s.getName))
        if(Options.cleanBuilds){
          logger.warning("BUsssssssssssssssssssssssssssssssssssssssssss " + plugin.homeDir.listFiles().toString)
          //io.recursiveClearDirectory(plugin.containerDir)
          io.recursiveClearDirectory(plugin.homeDir)
        }
        EntryPointsImpls
        val locs : collection.mutable.Map[Symbol, List[TempLocation]] = collection.mutable.HashMap[Symbol, List[TempLocation]]()
        val buildPath = Files.createDirectory(Paths.get(plugin.homeDir.getAbsolutePath, Options.dirPrefix + plugin.toolbox.getFormattedDateTimeString))

        def copy(multiTierModule : Symbol, entryPoint : TEntryPointDef) : Unit = {
          import java.nio.file.StandardCopyOption._
          val s : Path = Paths.get(buildPath.toAbsolutePath.toString, Options.containerDir, plugin.toolbox.getNormalizedFullNameDenominator(multiTierModule.asInstanceOf[plugin.global.Symbol]), entryPoint.getLocDenominator)
          io.createDirRecursively(s) match{
            case Some(d) =>
              io.copyContentOfDirectory(plugin.outputDir.toPath, Paths.get(d.getAbsolutePath, "classfiles"))
              locs update (multiTierModule, locs.getOrElse(multiTierModule, List[TempLocation]()) :+ TempLocation(entryPoint.containerEntryClass.asInstanceOf[plugin.global.Symbol].fullName, d.toPath, entryPoint.asSimplifiedEntryPoints()))
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

        PeerDefs.foreach(peer =>
          dependencyResolver.getAssocEntryPointsOfPeer(EntryPointsImpls.asInstanceOf[dependencyResolver.plugin.TEntryPointMap], peer.asInstanceOf[dependencyResolver.plugin.TAbstractClassDef])
            .foreach(e => copy(peer.module.asInstanceOf[Symbol], e)))

        val builder = new Builder(plugin.io).getBuilder(locs.toMap.map(k => (k._1.nameString, k._2)), buildPath.toFile)
        val composer = new Compose(plugin.io)(buildPath.toFile).getComposer

        var t0 = System.nanoTime()
        logger.info(s"first: ${(t0-s1)/1000000000}")
        //plugin.classPath.asClassPathString
        builder.collectLibraryDependencies(buildPath)
        builder.buildJARS()
        builder.buildCMDExec()
        builder.buildDockerFiles()
        builder.createReadme(buildPath)
        var t1 = System.nanoTime()

        logger.info(s"t1: ${(t1-t0)/1000000000}")

        if(Options.stage >= Options.image){
          builder.buildDockerBaseImageBuildScripts()
          t0 = System.nanoTime()
          logger.info(s"t2: ${(t0-t1)/1000000000}")
          builder.buildDockerImageBuildScripts()
          t1 = System.nanoTime()
          logger.info(s"t3: ${(t1-t0)/1000000000}")
          builder.buildDockerRunScripts()
          t0 = System.nanoTime()
          logger.info(s"t4: ${(t0-t1)/1000000000}")
          builder.buildDockerImages()
          t1 = System.nanoTime()
          logger.info(s"t5: ${(t1-t0)/1000000000}")
          if(Options.saveImages)
            builder.saveImageBackups()
          t0 = System.nanoTime()
          logger.info(s"t5: ${(t0-t1)/1000000000}")
          //runner.runLandscape(locs)
        }
        if(Options.stage >= Options.publish) {
          /**builder.publishDockerImagesToRepo()*/ //todo uncomment
        }
        t1 = System.nanoTime()
        logger.info(s"t6: ${(t1-t0)/1000000000}")
        if(Options.stage >= Options.compose){
          //todo were here
          locs.foreach(l => composer.buildDockerCompose(l._1.nameString, l._2))
          t0 = System.nanoTime()
          logger.info(s"t7: ${(t0-t1)/1000000000}")
          val stacks = locs.toList.map(_._1.nameString)
          composer.buildDockerSwarm(stacks)
          t1 = System.nanoTime()
          logger.info(s"t8: ${(t1-t0)/1000000000}")
          composer.buildDockerSwarmStop(stacks)
          t0 = System.nanoTime()
          logger.info(s"t9: ${(t0-t1)/1000000000}")
          composer.buildDockerStack(stacks)
          t1 = System.nanoTime()
          logger.info(s"t10: ${(t1-t0)/1000000000}")

          //compose.runDockerSwarm()
          //runner.Swarm.init()
          //runner.Swarm.deploy("test", Paths.get(outputDir.toString, "docker-compose.yml"))
        }
        //if(Options.stage.id > 2)
          //runner.runContainer(null)

        logger.info(s"Containerization plugin finished, deployment path: ${ buildPath.toString }")
        var s2 = System.nanoTime()
        logger.info(s"bp: ${(s2-s1)/1000000000}")
      }
    }
  }
}