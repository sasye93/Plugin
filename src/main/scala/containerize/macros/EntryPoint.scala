package containerize.macros

import loci.Peer

import scala.reflect.macros.whitebox.Context
import scala.annotation.{StaticAnnotation, compileTimeOnly}

import scala.language.experimental.macros

class forPeer(value: Peer) extends StaticAnnotation

@compileTimeOnly("enable macro paradise to expand macro annotations")
class EntryPoint extends StaticAnnotation {
  def macroTransform(annottees: Any*) = macro EntryPointImpl.impl
}

object EntryPointImpl {
  def impl(c: Context)(annottees: c.Expr[Any]*): c.Expr[Any] = {
    c.Expr[Any](null)
  }
}
