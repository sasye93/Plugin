package loci.container

import scala.annotation.{StaticAnnotation, compileTimeOnly}
import scala.language.experimental.macros
import scala.reflect.macros.whitebox

@compileTimeOnly("enable macro paradise to expand macro annotations")
class cscript(path : String) extends StaticAnnotation {
  def macroTransform(annottees: Any*) : Any = macro loci.container.ScriptImpl.impl
}

object ScriptImpl {

  val scriptPathDenoter : String = "containerizeServiceScriptPathLocation_"

  def impl(c : whitebox.Context)(annottees: c.Expr[Any]*): c.Expr[Any] = {

    import c.universe._

    //todo this will only hit for module, class?
    annottees.map(_.tree).toList match {
      case ModuleDef(mods, name, impl) :: Nil =>

        val t = (c.prefix.tree match {
          case q"new cscript(path=$s)" if s.toString.matches("^\".*\"$") => s
          case q"new cscript($s)" if s.toString.matches("^\".*\"$") => s
          case _ => c.abort(c.enclosingPosition, "Invalid @cscript annotation style. Use '@cscript(PATH), e.g. @cscript(\"scripts/mycfg.xml\").")
        }).toString.replaceAll("\"", "")

        c.Expr[Any](ModuleDef(mods, name, Template(impl.parents, impl.self, q"private lazy val $scriptPathDenoter : String = ${scriptPathDenoter + t}" :: impl.body)))
      case _ => c.abort(c.enclosingPosition, "Invalid annotation: @cscript must prepend module object.")
    }

  }
}
