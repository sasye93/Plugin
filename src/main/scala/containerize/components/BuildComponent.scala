package containerize.components

import java.util.concurrent.atomic.AtomicBoolean
import java.io.File
import java.nio.file.{Files, Path, Paths}

import containerize.IO.IO
import containerize.main.Containerize
import containerize.types.TempLocation

import scala.collection.mutable
import scala.tools.nsc.plugins.PluginComponent
import scala.reflect.internal.Phase
import scala.reflect.io.AbstractFile
import scala.tools.nsc.Global

class BuildComponent[+C <: Containerize](val global : Global)(val plugin : C) extends PluginComponent{

  import plugin._
  import global._

  //type TAbstractClassDef = AbstractClassDef[plugin.global.Type, plugin.global.TypeName, plugin.global.Symbol]

  val io : IO = new IO(plugin)
  val component: PluginComponent = this

  override val runsAfter : List[String] = List[String]("jvm")
  override val runsRightAfter: Option[String] = Some("jvm")
  override val phaseName : String = plugin.name + "-build"

  def newPhase(_prev: Phase) = new BuildPhase(_prev)

  class BuildPhase(prev: Phase) extends StdPhase(prev) {

    override def name = phaseName

    val executed : AtomicBoolean = new AtomicBoolean()

    def apply(unit: CompilationUnit) : Unit ={

      if(executed.compareAndSet(false, true)) {
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

        def copyToTempDir(workDir : File = plugin.workDir, entryPoints : collection.immutable.Map[plugin.global.ClassSymbol, TEntryPointImplDef] = EntryPointsImpls.toMap) : List[TempLocation] = {
          import java.io._

          var locs : List[TempLocation] = List[TempLocation]()
          try{
            val tempPath = Files.createTempDirectory(Paths.get(workDir.getAbsolutePath), "_LOCI_CONTAINERIZE_")

            def copy(entryPoint : TEntryPointImplDef) : Unit = {
              import java.nio.file.StandardCopyOption._
              val tempSubPath = Files.createTempDirectory(tempPath, "_" + entryPoint._containerEntryClass.fullName + "_" + entryPoint._containerPeerClass.fullName)

              io.copyContentOfDirectory(workDir.toPath, Paths.get(tempSubPath.toString, "classfiles"), true, "_LOCI_CONTAINERIZE_")
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

              locs = locs :+ TempLocation(entryPoint._containerEntryClass.asInstanceOf[plugin.global.Symbol].fullName, tempSubPath, entryPoint.asSimpleMap())
              //tempSubPath.toFile.deleteOnExit()
            }

            entryPoints.foreach(e => copy(e._2))
          }
          catch{
            case e: IOException => reporter.error(null, "error creating temporary directory: " + e.printStackTrace)
            case e: SecurityException => reporter.error(null, "security exception when trying to create temporary directory: " + e.printStackTrace)
            case e: Throwable => reporter.error(null, "unknown error when creating temporary directory: " + e.printStackTrace)
          }
          locs
        }

        //reporter.warning(null, global.genBCode.postProcessorFrontendAccess.getEntryPoints.toString)

        val locs = copyToTempDir()

        val builder = new containerize.build.Builder(plugin)(reporter).getBuilder(locs, Paths.get(workDir.getAbsolutePath))
        val runner : containerize.build.Runner = new containerize.build.Runner(reporter)

        /**
        val depends = io.listDependencies(Paths.get("C:\\Users\\Simon S\\Dropbox\\Masterarbeit\\Code\\examples-simple-master2\\lib_managed"))
                                      .filterNot(d => d.toAbsolutePath.endsWith("-source.jar"))
          */
          //todo excluding java home really ok? ext libs here are unique!
        val depends : List[Path] = plugin.classPath.asClassPathString.split(";").toList.map(Paths.get(_)).filterNot(_.startsWith(System.getProperties.get("java.home").toString))

        //plugin.classPath.asClassPathString
        builder.collectLibraryDependencies(depends)
        builder.buildJARS(depends)
        builder.buildCMDExec()
        builder.buildDockerFiles()
        builder.buildDockerImages()
        //runner.runContainer(null)

        if(containerize.options.Options.stage.id > 1 && false)
          builder.buildDockerImages()
      }
    }
  }
}