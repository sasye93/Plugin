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

class BuildComponent[+C <: Containerize](implicit val plugin : C) extends PluginComponent{

  implicit val global : Global = plugin.global
  implicit val parent : C = plugin

  import plugin._
  import global._
  //type TAbstractClassDef = AbstractClassDef[plugin.global.Type, plugin.global.TypeName, plugin.global.Symbol]

  val component: PluginComponent = this

  private val dependencyResolver : DependencyResolver[C] = new DependencyResolver[C]()

  override val runsAfter : List[String] = List[String]("jvm")
  override val runsRightAfter: Option[String] = Some("jvm")
  override val phaseName : String = plugin.name + "-build"

  def newPhase(_prev: Phase) = new BuildPhase(_prev)

  class BuildPhase(prev: Phase) extends StdPhase(prev) {

    override def name: String = phaseName

    val executed : AtomicBoolean = new AtomicBoolean()

    def apply(unit: CompilationUnit) : Unit ={

      if(Options.containerize && executed.compareAndSet(false, true)) {
        reporter.warning(null, "CALL END")

        def pathify(aClass : TAbstractClassDef) : TAbstractClassDef = {
          val classFile : AbstractFile = global.genBCode.postProcessorFrontendAccess.backendClassPath.findClassFile(aClass.classSymbol.javaClassName).orNull
          if(classFile == null) reporter.warning(null, s"output file for class not found: ${ aClass.classSymbol.javaClassName }")

          aClass.copy(outputPath = global.genBCode.postProcessorFrontendAccess.compilerSettings.outputDirectory(classFile).file, filePath = classFile)
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

        logger.warning("BUsssssssssssssssssssssssssssssssssssssssssss " + plugin.containerDir.listFiles().foldLeft("")((b,s) => b + s.getName))
        if(Options.cleanBuilds){
          logger.warning("BUsssssssssssssssssssssssssssssssssssssssssss " + plugin.containerDir.listFiles().toString)
          //io.recursiveClearDirectory(plugin.containerDir)
        }
        io.recursiveClearDirectory(plugin.containerDir)

        var locs : List[TempLocation] = List[TempLocation]()
        val buildPath = Files.createDirectory(Paths.get(plugin.containerDir.getAbsolutePath, Options.dirPrefix + plugin.toolbox.getFormattedDateTimeString()))

        try{
          def copy(entryPoint : TEntryPointDef) : Unit = {
            import java.nio.file.StandardCopyOption._
            val buildSubPath = Files.createDirectory(Paths.get(buildPath.toAbsolutePath.toString, entryPoint.getLocDenominator))

            io.copyContentOfDirectory(plugin.outputDir.toPath, Paths.get(buildSubPath.toAbsolutePath.toString, "classfiles"))
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

            locs = locs :+ TempLocation(entryPoint.containerEntryClass.asInstanceOf[plugin.global.Symbol].fullName, buildSubPath, entryPoint.asSimplifiedEntryPoints())
            //tempSubPath.toFile.deleteOnExit()
          }

          EntryPointsImpls.foreach(e => copy(e._2))
        }
        catch{
          case e: IOException => reporter.error(null, "error creating temporary directory: " + e.printStackTrace)
          case e: SecurityException => reporter.error(null, "security exception when trying to create temporary directory: " + e.printStackTrace)
          case e: Throwable => reporter.error(null, "unknown error when creating temporary directory: " + e.printStackTrace)
        }

        val network = new Network(plugin.io)(buildPath)
        val builder = new Builder(plugin.io)(network).getBuilder(locs, buildPath.toFile)
        val composer = new Compose(plugin.io).getComposer(locs, buildPath.toFile)

        val depends : List[Path] = dependencyResolver.classPathDependencies()

        //plugin.classPath.asClassPathString
        builder.collectLibraryDependencies(depends, buildPath)
        builder.buildJARS(depends)
        builder.buildCMDExec()
        builder.buildDockerFiles()
        builder.distributeReadmeFiles(buildPath)
        network.buildSetupScript()
        network.buildNetwork()

        if(Options.stage >= Options.image){
          builder.buildDockerBaseImageBuildScripts()
          builder.buildDockerImageBuildScripts()
          builder.buildDockerStartScripts()
          builder.buildDockerStopScripts()
          builder.buildDockerImages()
          runner.runLandscape(locs)
        }
        if(Options.stage >= Options.publish){
          builder.publishDockerImagesToRepo()
          composer.buildDockerCompose()
          //runner.Swarm.init()
          //runner.Swarm.deploy("test", Paths.get(outputDir.toString, "docker-compose.yml"))
        }
        //if(Options.stage.id > 2)
          //runner.runContainer(null)

      }
    }
  }
}