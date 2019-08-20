package loci.containerize.main

import java.io.File
import java.nio.file.Paths
import java.text.SimpleDateFormat
import java.util.Calendar
import java.nio.file.Path

import loci.containerize.IO.Logger
import loci.containerize.container.Runner

import scala.reflect.io.AbstractFile
import scala.tools.nsc.plugins.{Plugin, PluginComponent}
import scala.tools.nsc.{Global, util}
import loci.containerize.Options
import loci.containerize.components._
import loci.containerize.IO.IO
import loci.containerize.types.ContainerEntryPoint

import scala.collection.mutable
import scala.collection.immutable

/**
  * you can provide options using "-P:loci.containerize:<opt1>,<opt2>,..."
  *
  */

//todo output path not working if not .
//todo supports recursive project folder structure?
//todo output puth not hardcoded
//todo cmd is windows, use .sh?

class Containerize(val global: Global) extends Plugin with java.io.Closeable {
  import global._

  implicit val plugin : Containerize = this
  implicit val logger : Logger = new Logger(global.reporter)
  implicit val io : IO = new IO()

  val name : String = Options.pluginName
  val description : String = Options.pluginDescription

  val components : List[PluginComponent] = List[PluginComponent](
    new AnalyzeComponent[Containerize](),
    new BuildComponent[Containerize]()
  )

  val outputDir : File = Paths.get(global.currentSettings.outputDirs.getSingleOutput.getOrElse(Options.targetDir).toString).toFile
  val homeDir : File = Paths.get(outputDir.getParentFile.getAbsolutePath, Options.dir).toFile
  val classPath : util.ClassPath = global.classPath

  val runner : Runner = new Runner(logger)

  var PeerDefs : mutable.MutableList[TAbstractClassDef] = mutable.MutableList[TAbstractClassDef]()
  //todo algos always operate on here, mutating. change!
  var EntryPointsImpls : TEntryPointMap = new TEntryPointMap()

  type TAbstractClassDef = AbstractClassDef[Type, TypeName, Symbol]
  type TEntryPointDef = ContainerEntryPoint[Containerize]
  type TEntryPointMap = mutable.HashMap[ClassSymbol, TEntryPointDef]

  //todo classfiles as ref?
  //todo replace null with option
  case class AbstractClassDef[T <: global.Type, TN <: global.TypeName, S <: global.Symbol](
                               packageName : String,
                               className : TN,
                               classSymbol : S,
                               parentType : List[T],
                               outputPath : File = null,
                               filePath : AbstractFile = null,
                               //classFiles : List[AbstractClassDef[Type, TypeName, Symbol]] = List() //currently unused
                             )

  override def init(options: List[String], error: String => Unit): Boolean = {
    processOptions(options, error)
    if(runner.dockerRun()){
      runner.dockerCleanup()
      runner.dockerLogin()

      (homeDir.exists() && homeDir.isDirectory) || homeDir.mkdir()
    }
    else false
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

  object toolbox{
    def weakSymbolCompare(symbol1 : Symbol, symbol2 : Symbol) : Boolean = symbol1.fullName == symbol2.fullName
    def getFormattedDateTimeString: String = new SimpleDateFormat("yyyyMMdd_HHmmss").format(Calendar.getInstance().getTime)
    def toUnixString(p : Path): String = p.toString.replace("\\", "/")
  }
}