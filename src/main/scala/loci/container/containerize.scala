package loci.container

import scala.annotation.{StaticAnnotation, compileTimeOnly}
import scala.language.experimental.macros
import scala.reflect.macros.whitebox.Context

@compileTimeOnly("enable macro paradise to expand macro annotations")
class containerize extends StaticAnnotation {
  def macroTransform(annottees: Any*) = macro loci.container.ContainerizeImpl.impl
}

object ContainerizeImpl {

  def impl(c: Context)(annottees: c.Expr[Any]*): c.Expr[Any] = {
    import c.universe._
    println(showRaw(annottees map (_.tree) toList))
    annottees map (_.tree) toList match {
      case ModuleDef(mods, name, impl) :: Nil => impl match{
        case Template(parents, self, body) =>
          val p = {
            if (!parents.exists(x => x match {
              case Ident(c) => c match {
                case TypeName(n) => n == "Containerized"
                case _ => false
              }
              case _ => false
            }))
              parents :+ Ident(TypeName(c.symbolOf[Containerized].asClass.name.toString))
            else
              parents
          }
          c.Expr[Any](ModuleDef(mods, name, Template(p, self, body)))
      }
      case _ => c.abort(c.enclosingPosition, "Invalid annotation: @containerize must prepend module object.")
    }

  }
}
