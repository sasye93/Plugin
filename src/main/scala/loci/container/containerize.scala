/**
  * Implementation of the @containerize annotation.
  * If an object is annotated with @containerize, it will be turned into a microservice domain,
  * and the peers declared inside it are enabled for containerization.
  * @author Simon Schönwälder
  * @version 1.0
  */

package loci.container

import loci.impl.IO.{IO, Logger}
import loci.impl.Options
import loci.impl.types.{SimplifiedContainerModule, SimplifiedPeerDefinition}

import scala.annotation.{StaticAnnotation, compileTimeOnly}
import scala.language.experimental.macros
import scala.reflect.macros.blackbox
import scala.language.implicitConversions

/**
  * @param config Optional config passed to this macro, @see loci.impl.types.ModuleConfig
  */
@compileTimeOnly("enable macro paradise to expand macro annotations")
class containerize(config : String = "") extends StaticAnnotation {
  def macroTransform(annottees: Any*) : Any = macro loci.container.ContainerizeImpl.impl
}

object ContainerizeImpl {

  def impl(c : blackbox.Context)(annottees: c.Expr[Any]*): c.Expr[Any] = {

    //todo duplicate
    val tc = new Tools.TypeConverter(c)
    import c.universe._
    import tc._

    implicit def convertCtoTC(t : c.Tree) : tc.typeContext.Tree = t.asInstanceOf[tc.typeContext.Tree]
    implicit def convertTCtoC(t : tc.typeContext.Tree) : c.Tree = t.asInstanceOf[c.Tree]
    //todo prevent null pointer
    //def tpeType(x : Tree) : Type = tpe(x).asInstanceOf[c.Tree].tpe.asInstanceOf[c.Type]//orElse NoType

    annottees.map(_.tree).toList match {
      case (m : ModuleDef) :: Nil =>

        /**
          * extract macro argument.
          */
        val config : Option[String] = c.prefix.tree match {
          case q"new $s(config=$p)" if p.toString.matches("^\"(.|\n)+\"") => Some(p.toString.stripPrefix("\"").stripSuffix("\""))
          case q"new $s($p)" if p.toString.matches("^\"(.|\n)+\"") => Some(p.toString.stripPrefix("\"").stripSuffix("\""))
          case q"new $s($p)" => if(p.nonEmpty) c.warning(c.enclosingPosition, s"Did not recognize config provided, : $p"); None
          case q"new $s" => None
          case _ => c.abort(c.enclosingPosition, "Invalid @containerize annotation style. Use '@containerize(path : String = \"\"), e.g. @containerize(\"scripts/mycfg.xml\") or without parameter.")
        }

        val mod = tpe(m).asInstanceOf[c.Tree]
        val moduleName = mod.symbol.fullName

        c.info(c.enclosingPosition, s"Found and processing @containerize: ${moduleName}.", true)

        val peers : List[SimplifiedPeerDefinition] =
          mod.symbol.typeSignature.members.collect({
            case m : Symbol if m.isType &&
              ( m.annotations.exists(_.tree.tpe =:= c.typeOf[loci.peer]) || //If @peer has not yet been processed.
                m.typeSignature.baseClasses.exists(_.typeSignature <:< c.typeOf[loci.language.impl.Peer]))  //If @peer has already been processed.
               => SimplifiedPeerDefinition(tpe(mod).symbol.fullName + "." + m.name.toString)
          }).toList
        /**
          * pickle the module info, so it can be used by the build stage.
          *  */
        implicit val logger : Logger = new Logger(c.universe.asInstanceOf[tools.nsc.Global].reporter)
        val io = new IO()
        io.createDir(Options.tempDir)
        //if(!mod.symbol.contentEquals(NoSymbol.fullName)) todo active
        io.serialize(new SimplifiedContainerModule(moduleName, peers, config), Options.tempDir.resolve(mod.symbol.fullName + ".mod"))

        c.Expr[Any](m)
      case _ => c.abort(c.enclosingPosition, "Invalid annotation: @loci.containerize must prepend a module object (object or case object).")
    }
  }
}