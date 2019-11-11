/**
  * This is the main class of the containerization-build plugin (the build stage of the extension).
  */
package loci.container.build.main

import java.io.File
import java.nio.file.Paths

import loci.container.build.IO.{ContainerEntryPoint, ContainerModule, IO, Logger, SimplifiedContainerEntryPoint, SimplifiedContainerModule, SimplifiedPeerDefinition}
import loci.container.build.images.Runner
import loci.container.build.Options
import loci.container.build.components.{DependencyResolver, _}

import scala.tools.nsc.plugins.{Plugin, PluginComponent}
import scala.tools.nsc.{Global, util}

//todo supports recursive project folder structure?

class Containerize(val global: Global) extends Plugin with java.io.Closeable {

  implicit val plugin : Containerize = this
  implicit val logger : Logger = new Logger(global.reporter)
  implicit val io : IO = new IO()

  val name : String = Options.pluginName
  val description : String = Options.pluginDescription

  val components : List[PluginComponent] = List[PluginComponent](
    new BuildComponent()
  )

  //output directory of generated files, usually /target/scala-XXX/.
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

}