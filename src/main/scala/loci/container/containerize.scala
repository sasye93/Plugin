package loci.container

import java.nio.file.Paths

import loci.containerize.IO.{IO, Logger}
import loci.containerize.Options
import loci.containerize.types.{SimplifiedContainerEntryPoint, SimplifiedContainerModule, SimplifiedPeerDefinition}

import scala.annotation.{StaticAnnotation, compileTimeOnly}
import scala.language.experimental.macros
import scala.reflect.macros.blackbox
import scala.reflect.runtime.universe

@compileTimeOnly("enable macro paradise to expand macro annotations")
class containerize extends StaticAnnotation {
  def macroTransform(annottees: Any*) : Any = macro loci.container.ContainerizeImpl.impl
}

object ContainerizeImpl {

  def impl(c : blackbox.Context)(annottees: c.Expr[Any]*): c.Expr[Any] = {

    //todo dupl, make it somehow different
    val tc = new Tools.TypeConverter(c)
    import c.universe._
    import tc._

    //todo dupl, make it somehow different
    implicit def convertCtoTC(t : c.Tree) : tc.typeContext.Tree = t.asInstanceOf[tc.typeContext.Tree]
    implicit def convertTCtoC(t : tc.typeContext.Tree) : c.Tree = t.asInstanceOf[c.Tree]
    //todo prevent null pointer
    //def tpeType(x : Tree) : Type = tpe(x).asInstanceOf[c.Tree].tpe.asInstanceOf[c.Type]//orElse NoType

    //todo this will only hit for module, class?
    annottees.map(_.tree).toList match {
      case (m : ModuleDef) :: Nil => m.impl match{
        case Template(parents, self, body) =>
          c.info(null, "this is it : " + showRaw(body), true)
          val mod = tpe(m).asInstanceOf[c.Tree]
          val peers : List[SimplifiedPeerDefinition] =
          mod.symbol.typeSignature.members.collect({
            case m : Symbol if(m.isType &&
                ( m.annotations.exists(_.tree.tpe =:= c.typeOf[loci.peer]) || //If @peer has not yet been processed.
                  m.typeSignature.baseClasses.exists(_.typeSignature <:< c.typeOf[loci.language.impl.Peer]))  //If @peer has already been processed.
              ) => SimplifiedPeerDefinition(tpe(mod).symbol.fullName + "." + m.name.toString)
          }).toList
          c.info(c.enclosingPosition, "members: " + mod.tpe.members.map(x => (x.name, x.typeSignature)).toString, true)
          c.info(c.enclosingPosition, "peers: " + peers.map(mod.symbol.fullName + "." + _.className).toString, true)
          /**
           * save it.
           *  */
          implicit val logger : Logger = new Logger(c.universe.asInstanceOf[tools.nsc.Global].reporter)
          val io = new IO()
          io.createDir(Options.tempDir)
          io.serialize(new SimplifiedContainerModule(mod.symbol.fullName, peers), Options.tempDir.resolve(mod.symbol.fullName + ".mod"))

          /**
          if(c.symbol.isModuleOrModuleClass && hasContainerizationAnnot(c.symbol)){
            getPeers(c).foreach{ p =>
              logger.warning("P : " + p)
              PeerDefs += new TAbstractClassDef(
                topLevelModule(p),
                p.enclosingPackage.javaClassName,
                p.name.asInstanceOf[plugin.global.TypeName],
                p.asInstanceOf[plugin.global.Symbol],
                p.parentSymbols.map(_.tpe.asInstanceOf[plugin.global.Type])
              )
            }
          }
           */
          /**
          val p = {
            if (!parents.exists({
              case Ident(c) => c match {
                case TypeName(n) => n == "ContainerizedModule"
                case _ => false
              }
              case _ => false
            }))
              parents :+ Ident(TypeName(c.symbolOf[ContainerizedModule].asClass.name.toString))
            else
              parents
          }
           */
          c.Expr[Any](m)
      }
      case _ => c.abort(c.enclosingPosition, "Invalid annotation: @loci.containerize must prepend a module object (object or case object).")
    }

  }
}
