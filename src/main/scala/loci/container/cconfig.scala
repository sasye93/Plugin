package loci.container

import scala.annotation.{StaticAnnotation, compileTimeOnly}
import scala.language.experimental.macros
import scala.reflect.macros.blackbox

@compileTimeOnly("enable macro paradise to expand macro annotations")
class cconfig(path : String) extends StaticAnnotation {
  def macroTransform(annottees: Any*) : Any = macro loci.container.ConfigImpl.impl
}

object ConfigImpl {

  val configPathDenoter : String = "containerizeServiceConfigPathLocation_"

  def impl(c : blackbox.Context)(annottees: c.Expr[Any]*): c.Expr[Any] = {

    import c.universe._

    //todo this will only hit for module, class?
    annottees.map(_.tree).toList match {
      case ModuleDef(mods, name, impl) :: Nil =>

          val t = (c.prefix.tree match {
            case q"new cconfig(path=$s)" if s.toString.matches("^\".*\"$") => s
            case q"new cconfig($s)" if s.toString.matches("^\".*\"$") => s
            case _ => c.abort(c.enclosingPosition, "Invalid @cconfig annotation style. Use '@cconfig(PATH), e.g. @cconfig(\"/configs/mycfg.json\").")
          }).toString.replaceAll("\"", "")

          c.Expr[Any](ModuleDef(mods, name, Template(impl.parents, impl.self, q"private lazy val $configPathDenoter : String = ${configPathDenoter + t}" :: impl.body)))
      case _ => c.abort(c.enclosingPosition, "Invalid annotation: @cconfig must prepend module object.")
    }

  }
}
