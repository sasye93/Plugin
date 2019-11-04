package loci.impl.main

import java.io.File
import java.nio.file.Paths
import java.text.SimpleDateFormat
import java.util.Calendar
import java.nio.file.Path

import loci.impl.AST.DependencyResolver
import loci.impl.IO.Logger
import loci.impl.container.Runner

import scala.reflect.io.AbstractFile
import scala.tools.nsc.plugins.{Plugin, PluginComponent}
import scala.tools.nsc.{Global, util}
import loci.impl.Options
import loci.impl.components._
import loci.impl.IO.IO
import loci.impl.types._

import scala.collection.mutable
import scala.collection.immutable

/**
  * you can provide options using "-P:containerize:<opt1>,<opt2>,..."
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
    new BuildComponent()
  )

  val outputDir : File = Paths.get(global.currentSettings.outputDirs.getSingleOutput.getOrElse(Options.targetDir).toString).toFile
  val homeDir : File = Paths.get(outputDir.getParentFile.getAbsolutePath, Options.dir).toFile
  val classPath : util.ClassPath = global.classPath

  val runner : Runner = new Runner(logger)
  val dependencyResolver : DependencyResolver = new DependencyResolver()

  //todo algos always operate on here, mutating. change!
  @deprecated("1") private[impl] var PeerDefs : mutable.MutableList[TAbstractClassDef] = mutable.MutableList[TAbstractClassDef]()
  @deprecated("1") private[impl] var EntryPointsImpls : TEntryPointMap = new TEntryPointMap()

  @deprecated("1") type TAbstractClassDef = AbstractClassDef[Type, TypeName, Symbol]
  @deprecated("1") type TEntryPointDef = ContainerEntryPoint
  @deprecated("1") type TEntryPointMap = mutable.HashMap[ClassSymbol, TEntryPointDef]

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

  class ccSymbol(s : Symbol){
  }

  //todo classfiles as ref?
  @deprecated("1") case class AbstractClassDef[T <: global.Type, TN <: global.TypeName, S <: global.Symbol](
                                                                                            module : S,
                                                                                            packageName : String,
                                                                                            className : TN,
                                                                                            classSymbol : S,
                                                                                            parentType : List[T],
                                                                                            outputPath : Option[File] = None, //todo not used
                                                                                            filePath : Option[AbstractFile] = None, //todo not used
                                                                                            //classFiles : List[AbstractClassDef[Type, TypeName, Symbol]] = List() //currently unused
                                                                                          )

  override def init(options: List[String], error: String => Unit): Boolean = {
    processOptions(options, error)
    if(runner.dockerRun() && runner.requirementsCheck()){
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

  @deprecated("use Options.toolbox instead") object toolbox{
    def weakSymbolCompare(symbol1 : Symbol, symbol2 : Symbol) : Boolean = symbol1.fullName == symbol2.fullName
    def getFormattedDateTimeString: String = new SimpleDateFormat("yyyyMMdd_HHmmss").format(Calendar.getInstance().getTime)
    def toUnixString(p : Path): String = p.toString.replace("\\", "/")
    def getNameDenominator(s : String) : String = loci.container.Tools.getIpString(s)
  }
}