package containerize.main

import java.io.File
import java.nio.file.Paths

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
    new containerize.components.AnalyzeComponent(Containerize.this.global, this),
    new containerize.components.BuildComponent(Containerize.this.global, this)
  )

  val workDir : File = Paths.get(global.settings.outputDirs.getSingleOutput.getOrElse(Options.targetDir).toString).toFile
  val classPath : util.ClassPath = global.classPath

  var ClassDefs : mutable.MutableList[TAbstractClassDef] = mutable.MutableList[TAbstractClassDef]()
  var EntryPointDefs : mutable.MutableList[TEntryPointDef] = mutable.MutableList[TEntryPointDef]()

  type TAbstractClassDef = AbstractClassDef[Type, TypeName, Symbol]
  type TEntryPointDef = EntryPointDef[Symbol]

  //todo classfiles as ref?
  //todo replace null with option
  case class AbstractClassDef[T <: global.Type, TN <: global.TypeName, S <: global.Symbol](
                               isPeer : Boolean,
                               packageName : String,
                               className : TN,
                               classSymbol : S,
                               parentType : List[T],
                               outputPath : File = null,
                               filePath : AbstractFile = null,
                               classFiles : List[AbstractClassDef[Type, TypeName, Symbol]] = List(),
                               entryPoint : String = null
                             )

  case class EntryPointDef[S](abstractClass : TAbstractClassDef, peerSymbol : S, port : Integer = 0)

  override def processOptions(options: List[String], error: String => Unit): Unit = Options.processOptions(_,_)
  override val optionsHelp = Some("todo")
}