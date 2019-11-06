/**
  * Implementation of the @gateway annotation.
  * This is the same as @service and simply links to it, but with the difference of marking this as a gateway which will
  * trigger port opening in the service implementation.
  * @author Simon Schönwälder
  * @version 1.0
  */
package loci.container

import scala.annotation.{StaticAnnotation, compileTimeOnly}
import scala.language.experimental.macros

/**
  * @param config Optional config passed to this macro, @see loci.impl.types.ContainerConfig
  */
@compileTimeOnly("enable macro paradise to expand macro annotations")
class gateway(config: String = "") extends StaticAnnotation {
  def macroTransform(annottees: Any*) : Any = macro loci.container.ServiceImpl.impl
}
