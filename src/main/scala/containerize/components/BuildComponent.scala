package containerize.components

import java.util.concurrent.atomic.AtomicBoolean
import java.io.File
import java.nio.file.{Files, Path, Paths}

import containerize.main.Containerize
import containerize.types.TempLocation

import scala.tools.nsc.Global
import scala.tools.nsc.plugins.PluginComponent
import scala.reflect.internal.Phase
import scala.reflect.io.AbstractFile

class BuildComponent[G <: Global](val global : G, val plugin : Containerize) extends PluginComponent{

  import global._
  import plugin._

  type TAbstractClassDef = AbstractClassDef[plugin.global.Type, plugin.global.TypeName, plugin.global.Symbol]

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
          if(classFile == null) reporter.warning(null, "output file for class not found.")

          aClass.copy(outputPath = global.genBCode.postProcessorFrontendAccess.compilerSettings.outputDirectory(classFile).file, filePath = classFile)
        }

        ClassDefs = ClassDefs.map(c => {

          //for containers every container must have one entry point class where only this peer is used. ?
          //right now, this class must have same name as peer
          //todo automate
          val entryPoints = global.genBCode.postProcessorFrontendAccess.getEntryPoints
          if(entryPoints.isEmpty)
            reporter.warning(null, "no entry points found by compiler.")

          EntryPointDefs.toList.foreach(e => {
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


        def copyToTempDir(workDir : File = plugin.workDir, classes : List[TAbstractClassDef] = ClassDefs.toList) : List[TempLocation] = {
          import java.io._
          var locs : List[TempLocation] = List[TempLocation]()
          try{
            val tempPath = Files.createTempDirectory(Paths.get(workDir.getAbsolutePath), "_LOCI_CONTAINERIZE_")

            def copy(aClass : TAbstractClassDef) : Unit = {
              import java.nio.file.StandardCopyOption._
              val tempSubPath = Files.createTempDirectory(tempPath, "_" + aClass.classSymbol.javaClassName)
              aClass.classFiles.foreach(f => {
                //reporter.warning(null, f.filePath.toString)
                //reporter.warning(null, Paths.get(tempSubPath.toString, f.getName).toString)

                reporter.warning(null, f.filePath.path)
                val filePath : Path = Paths.get(f.filePath.path).normalize
                val filePathName = f.filePath.file.getName
                val packageSubPath = "/" + f.packageName.replace('.', '/') + "/"
                //reporter.warning(null, " LK" + Paths.get(tempSubPath.toString, packageSubPath))
                containerize.build.IO.createFolderStructure(Paths.get(tempSubPath.toString, packageSubPath))
                Files.copy(filePath, Paths.get(tempSubPath.toString, packageSubPath, filePathName.toString), REPLACE_EXISTING)

                //todo better
                val canonicalName = filePathName.replace("$", "")
                val parentFiles = Paths.get(f.filePath.file.getParent, canonicalName)
                if(Files.exists(parentFiles))
                  Files.copy(parentFiles, Paths.get(tempSubPath.toString, packageSubPath, canonicalName), REPLACE_EXISTING)

              })

              //Files.createFile(Paths.get(tempSubPath.toString, "MANIFEST.MF"))

              locs = locs :+ TempLocation(aClass.classSymbol.javaSimpleName.toString, tempSubPath, aClass.entryPoint)
              //tempSubPath.toFile.deleteOnExit()
            }

            classes.foreach(c => copy(c))
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

        val builder : containerize.build.Builder = new containerize.build.Builder(locs, reporter)

        builder.buildCMDExec(plugin.classPath.asClassPathString)
        builder.buildDockerFiles()
        builder.buildJARS()

        if(containerize.options.Options.stage.id > 1 && false)
          builder.buildDockerImages()
      }
    }
  }
}