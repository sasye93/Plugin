package containerize.main

import java.io.File
import java.nio.file.{Path, Paths}

import scala.reflect.io.AbstractFile
import scala.tools.nsc.plugins.{Plugin, PluginComponent}
import scala.tools.nsc.{Global, util}
import containerize.options.Options

import scala.collection.mutable

/**
  * you can provide parameters using "-P:containerize:<param>"
  *
  */

//todo output path not working if not .
//todo supports recursive project folder structure?
//todo output puth not hardcoded
//todo bat is windows, use .sh?

class Containerize(val global: Global) extends Plugin {
  import global._

  val name = "containerize"
  val description = "Extends ScalaLoci to provide compiler support for direct deployment of Peers to Containers"
  val components : List[PluginComponent] = List[PluginComponent](
    new containerize.components.AnalyzeComponent(global)(this),
    new containerize.components.BuildComponent(global)(this)
  )

  val workDir : File = Paths.get(global.settings.outputDirs.getSingleOutput.getOrElse(Options.targetDir).toString).toFile
  val classPath : util.ClassPath = global.classPath

  //todo rename peerdefs
  var PeerDefs : mutable.MutableList[TAbstractClassDef] = mutable.MutableList[TAbstractClassDef]()
  var EntryPointsImpls : mutable.HashMap[ClassSymbol, TEntryPointImplDef] = mutable.HashMap[ClassSymbol, TEntryPointImplDef]()

  type TAbstractClassDef = AbstractClassDef[Type, TypeName, Symbol]
  type TEntryPointImplDef = loci.container.ContainerEntryPointImpl[Global]

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

  override def processOptions(options: List[String], error: String => Unit): Unit = Options.processOptions(_,_)
  override val optionsHelp = Some("todo")

  object toolbox{
    def weakSymbolCompare(symbol1 : Symbol, symbol2 : Symbol) : Boolean = symbol1.fullName == symbol2.fullName
  }
}