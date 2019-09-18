package loci.container

import scala.annotation.{StaticAnnotation, compileTimeOnly}
import scala.language.experimental.macros
import scala.reflect.macros.blackbox

@compileTimeOnly("enable macro paradise to expand macro annotations")
class gateway(config: String = "") extends StaticAnnotation {
  def macroTransform(annottees: Any*) : Any = macro loci.container.ServiceImpl.impl
}
