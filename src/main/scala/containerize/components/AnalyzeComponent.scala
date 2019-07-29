package containerize.components

import containerize.main.Containerize
import containerize.options.Options
import containerize.AST.TreeTraverser

import scala.reflect.io.AbstractFile
import scala.tools.nsc.{Global, Phase}
import scala.tools.nsc.plugins.PluginComponent

class AnalyzeComponent[G <: Global](val global : G, val plugin : Containerize) extends PluginComponent {

  import global._
  import plugin._

  val component: PluginComponent = this

  override val runsAfter : List[String] = List[String]("delambdafy")
  //override val runsRightAfter: Option[String] = Some("typer")
  override val runsBefore : List[String] = List[String]("jvm")
  override val phaseName : String = plugin.name + "-analyze"

  //todo we could use a if here to see what phases already ran and decide right phase, not needing 2 components?
  def newPhase(_prev: Phase) = new ContainerizePhase(_prev)

  class ContainerizePhase(prev: Phase) extends StdPhase(prev) {

    override def name : String = phaseName

    def apply(unit: CompilationUnit): Unit = {

      import global._

      //todo enthält classes path, ist aber Option => kein verlass? gibt auch setsingleoutput, evtl als fallback harcoded target path? hmm vll über flag steuern

      val traverser : TreeTraverser[Global] = new TreeTraverser[Global](global, plugin)


      reporter.warning(null, global.settings.outputDirs.getSingleOutput.getOrElse(0).toString)
      reporter.warning(null, Options.targetDir.toString)
      /* not created yewt ...
              if(!(workDir.exists() && workDir.isDirectory))
                reporter.error(null, "class output directory does not exist at " + workDir.getAbsolutePath)
              else if(workDir.list.isEmpty)
                reporter.error(null, "no class files found at " + workDir.getAbsoluteFile)
      */

      reporter.warning(null, "CU DEPEND: " + unit.depends.toString)

      traverser.traverse(unit.body)
      traverser.dependencies()
      //traverser.ClassDefs.foreach(x => global.reporter.warning(null, x.classSymbol.javaClassName + x.parentType.toString))

      if(!ClassDefs.exists(_.isPeer)) reporter.warning(null, "no peer classes found.")

      import java.nio.file._

      //check if trhow err if dir empty
      //val list = workDir.list.map(new File(_)).toList
      //ClassDefs.map(x => global.reporter.warning(null, x.toString))
      //global.reporter.warning(null, "D")
      //traverser.ClassDefs.map(x => x match{ case AbstractClass(_1,_2,_3,_4,_5,_) => AbstractClass(_1,_2,_3,_4,_5,filterPeerFiles(x, list, traverser.ClassDefs)) }).foreach(x => x.classFiles.map(y => global.reporter.warning(null, x.className + ":" + y.getName)))

      //list.map(x => { global.reporter.warning(null, list.length.toString) })
      //val files = if (list != null) Option(list.length).getOrElse(0) else 0
      //global.reporter.warning(null, "classpath: " + global.classPath.asClassPathString)
      //global.reporter.warning(null, "filec: " + files.toString)



      //alternative call: component.afterOwnPhase;component.global.exitingPhase(this){


      //containerize.build.IO.clearTempDirs(locs)

      //val t = unit.body
      //     global.reporter.warning(null, t.toString)
      //  global.reporter.warning(null, showRaw(t))
      /*
      for ( tree @ Apply(Select(rcvr, nme.DIV), List(Literal(Constant(0)))) <- unit.body
            if true || rcvr.tpe <:< definitions.IntClass.tpe)
      {*/
      //}
    }
  }
}