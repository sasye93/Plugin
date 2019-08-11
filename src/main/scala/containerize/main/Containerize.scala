package containerize.main

import java.io.File
import java.nio.file.Paths

import containerize.IO.Logger
import containerize.build.Runner

import scala.reflect.io.AbstractFile
import scala.tools.nsc.plugins.{Plugin, PluginComponent}
import scala.tools.nsc.{Global, util}
import containerize.Options

import scala.collection.mutable

/**
  * you can provide options using "-P:containerize:<opt1>,<opt2>,..."
  *
  */

//todo output path not working if not .
//todo supports recursive project folder structure?
//todo output puth not hardcoded
//todo bat is windows, use .sh?

class Containerize(val global: Global) extends Plugin with java.io.Closeable {
  import global._

  val name = "containerize"
  val description = "Extends ScalaLoci to provide compiler support for direct deployment of Peers to Containers"
  val components : List[PluginComponent] = List[PluginComponent](
    new containerize.components.AnalyzeComponent(global)(this),
    new containerize.components.BuildComponent(global)(this)
  )

  val workDir : File = Paths.get(global.settings.outputDirs.getSingleOutput.getOrElse(Options.targetDir).toString).toFile
  val classPath : util.ClassPath = global.classPath

  val logger : Logger = new Logger(global.reporter)
  val runner : Runner = new Runner(logger)

  var PeerDefs : mutable.MutableList[TAbstractClassDef] = mutable.MutableList[TAbstractClassDef]()
  var EntryPointsImpls : mutable.HashMap[ClassSymbol, TEntryPointDef] = mutable.HashMap[ClassSymbol, TEntryPointDef]()

  type TAbstractClassDef = AbstractClassDef[Type, TypeName, Symbol]
  type TEntryPointDef = loci.container.ContainerEntryPoint[Global]

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
      true
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
  override val optionsHelp = Some("todo")

  object toolbox{
    def weakSymbolCompare(symbol1 : Symbol, symbol2 : Symbol) : Boolean = symbol1.fullName == symbol2.fullName
  }
}