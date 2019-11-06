/**
  * This is the main class of the containerization-build plugin (the build stage of the extension).
  */
package loci.impl.main

import java.io.File
import java.nio.file.{Path,Paths}
import java.text.SimpleDateFormat
import java.util.Calendar

import loci.impl.AST.DependencyResolver
import loci.impl.IO.Logger
import loci.impl.container.Runner
import loci.impl.Options
import loci.impl.components._
import loci.impl.IO.IO
import loci.impl.types._

import scala.tools.nsc.plugins.{Plugin, PluginComponent}
import scala.tools.nsc.{Global, util}

//todo output path not working if not .
//todo supports recursive project folder structure?
//todo cmd is windows, use .sh?

class Containerize(val global: Global) extends Plugin with java.io.Closeable {
  import global._

  implicit val plugin : Containerize = this
  implicit val logger : Logger = new Logger(global.reporter)
  implicit val io : IO = new IO()

  val name : String = Options.pluginName
  val description : String = Options.pluginDescription

  val components : List[PluginComponent] = List[PluginComponent](
    new BuildComponent()
  )

  lazy val outputDir : File = Paths.get(global.currentSettings.outputDirs.outputDirFor(global.currentSource.file).path).toFile
  lazy val homeDir : File = Paths.get(outputDir.getParentFile.getAbsolutePath, Options.dir).toFile
  lazy val classPath : util.ClassPath = global.classPath

  val runner : Runner = new Runner(logger)
  val dependencyResolver : DependencyResolver = new DependencyResolver()

  type TSimpleModuleDef = SimplifiedContainerModule
  type TModuleDef = ContainerModule
  type TSimpleEntryDef = SimplifiedContainerEntryPoint
  type TEntryDef = ContainerEntryPoint
  type TPeerDef = SimplifiedPeerDefinition
  type TSimpleEntryList = List[TSimpleEntryDef]
  type TEntryList = List[TEntryDef]
  type TSimpleModuleList = List[TSimpleModuleDef]
  type TModuleList = List[TModuleDef]
  type TPeerList = List[TPeerDef]

  override def init(options: List[String], error: String => Unit): Boolean = {
    processOptions(options, error)
    if(runner.dockerRun() && runner.requirementsCheck()){
      runner.dockerCleanup()
      runner.dockerLogin()
    }
    else{
      io.recursiveClearDirectory(Options.tempDir.toFile, self = true)
      logger.warning("Containerization Extension has been disabled because docker is not running or prerequisites are not met.")
      Options.initAbort = true
    }
    true
  }
  override def close() : Unit = {
    if(runner.dockerIsRunning()){
      runner.dockerLogout()
    }
  }
  override def processOptions(options: List[String], error: String => Unit): Unit = {
    Options.processOptions(options, error)
    Options.checkConstraints(logger)
  }
  override val optionsHelp = Some(Options.pluginHelp)

  @deprecated("use Options.toolbox instead") object toolbox{
    def weakSymbolCompare(symbol1 : Symbol, symbol2 : Symbol) : Boolean = symbol1.fullName == symbol2.fullName
    def getFormattedDateTimeString: String = new SimpleDateFormat("yyyyMMdd_HHmmss").format(Calendar.getInstance().getTime)
    def toUnixString(p : Path): String = p.toString.replace("\\", "/")
    def getNameDenominator(s : String) : String = loci.container.Tools.getIpString(s)
  }
}