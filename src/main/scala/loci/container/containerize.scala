package loci.container

import scala.annotation.{StaticAnnotation, compileTimeOnly}
import scala.language.experimental.macros
import scala.reflect.macros.whitebox

@compileTimeOnly("enable macro paradise to expand macro annotations")
class containerize extends StaticAnnotation {
  def macroTransform(annottees: Any*) : Any = macro loci.container.ContainerizeImpl.impl
}

object ContainerizeImpl {

  def impl(c : whitebox.Context)(annottees: c.Expr[Any]*): c.Expr[Any] = {
    import c.universe._

    //todo this will only hit for module, class?
    annottees.map(_.tree).toList match {
      case ModuleDef(mods, name, impl) :: Nil => impl match{
        case Template(parents, self, body) =>
          val p = {
            if (!parents.exists({
              case Ident(c) => c match {
                case TypeName(n) => n == "Containerized"
                case _ => false
              }
              case _ => false
            }))
              parents :+ Ident(TypeName(c.symbolOf[ContainerizedModule].asClass.name.toString))
            else
              parents
          }
          c.Expr[Any](ModuleDef(mods, name, Template(p, self, body)))
      }
      case _ => c.abort(c.enclosingPosition, "Invalid annotation: @loci.containerize must prepend module object.")
    }

  }
}
