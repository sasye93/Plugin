/**
  * Implementation of the @gateway annotation.
  * @author Simon Schönwälder
  * @version 1.0
  */
package loci.container

import scala.annotation.{StaticAnnotation, compileTimeOnly}
import scala.language.experimental.macros

@compileTimeOnly("enable macro paradise to expand macro annotations")
class gateway(config: String = "") extends StaticAnnotation {
  def macroTransform(annottees: Any*) : Any = macro loci.container.ServiceImpl.impl
}
