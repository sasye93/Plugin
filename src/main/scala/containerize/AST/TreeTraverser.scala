package containerize.AST

import containerize.main.Containerize
import containerize.options.Options

import scala.collection.mutable
import scala.reflect.io.AbstractFile
import scala.tools.nsc.Global

class TreeTraverser[G <: Global](val global : Global, val plugin : Containerize) {

  import global._
  import plugin._

  type TAbstractClassDef = AbstractClassDef[plugin.global.Type, plugin.global.TypeName, plugin.global.Symbol]

  //todo ousource
  private val PeerType = typeOf[loci.Peer]

  def traverse[T >: Tree](tree : T) : Unit = {
    TreeTraverser.traverse(tree.asInstanceOf[this.global.Tree])
  }
  val dependencies : () => Unit = TreeTraverser.dependencies

  private object TreeTraverser extends Traverser{

    //todo dependencies in other packages are not included
    //todo parent peer merken ?
    // todo kann man peer defs schachteln? glaub nich...
    override def traverse(tree: Tree): Unit = tree match {
      case a @ Annotated(_,_) => reporter.warning(null, "ANOT: " + a.toString)//test  only
      case c @ ClassDef(_, className, _, impl: Template) => impl match {
        case t @ Template(parents, _, body) =>
          val pars = parents.map(_.tpe)

          val aClassDef = new TAbstractClassDef(
            pars.exists(_ =:= PeerType),
            c.symbol.enclosingPackage.javaClassName,
            className.asInstanceOf[plugin.global.TypeName],
            c.symbol.asInstanceOf[plugin.global.Symbol],
            pars.map(_.asInstanceOf[plugin.global.Type])
          )

          //todo

          if(pars.contains(typeOf[loci.container.ContainerEntryPoint])){
            EntryPointDefs += EntryPointDef(aClassDef, null)
            reporter.warning(null, "ENTRY P CODE : " + t.toString)
          }

          else
            ClassDefs += aClassDef


          reporter.warning(null, pars.exists(_ =:= PeerType) + ":" + c.symbol.javaBinaryNameString.toString + ":"+ c.symbol.toString + ":"+ c.symbol.signatureString.toString + ":"+ c.symbol.fullLocationString.toString + ":"+ c.symbol.fullName.toString + ":")

          //reporter.info(tree.pos, classFile.path.toString, true)
          //reporter.info(tree.pos, global.genBCode.postProcessorFrontendAccess.compilerSettings.outputDirectory(classFile).file.toPath.toString, true )

          body.foreach(traverse)

      }
      case s @ Import(_,_) => reporter.warning(null, "IMPORT:   " + s.toString)
      case q @ _ => super.traverse(tree)
    }

    def dependencies() : Unit = {

      ClassDefs.foreach(x => reporter.warning(null, "____" + x.packageName + x.className))
      ClassDefs = ClassDefs.filter(_.isPeer).map(p => {

        val fileDependencyList : List[TAbstractClassDef] =
          ClassDefs.filter(x => x.isPeer && !x.equals(p)).foldLeft(ClassDefs)((list, x) =>
            list.filterNot(c => c.equals(x) || c.classSymbol.javaClassName.startsWith(x.classSymbol.javaClassName))
          ).toList
        //.map(c => Paths.get(c.filePath.path).normalize)
        //todo ok? we could also do outputpath + c.symbol.javaBinaryNameString.toString
        // global.reporter.warning(null, fileList.map(_.toAbsolutePath).toString)

        p.copy(classFiles = fileDependencyList)
      })
    }
  }
}
