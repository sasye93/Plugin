/**
  * Provides helper functions for developers.
  * @author Simon Schönwälder
  * @version 1.0
  */
package loci.container

package object Tools extends AnyRef {
  final def resolveIp(c : Any) : String = getIpString(c.getClass.getTypeName)
  final def publicIp : String = "0.0.0.0"
  final def localhost : String = "127.0.0.1"

  final def globalDbIp(module : Any) : String = s"mongodb://${ resolveIp(module) }_globaldb" //todo if supporting more than mongo, don't make this hardcoded.
  final def localDbIp(service : Any) : String = s"mongodb://${ resolveIp(service) }_localdb" //todo if supporting more than mongo, don't make this hardcoded.

  private[container] final class TypeConverter(val typeContext : scala.reflect.macros.blackbox.Context){
    import typeContext._

    def tpe(x : Tree) : Tree = {
      try{
        typeContext.typecheck(x.asInstanceOf[typeContext.Tree], silent=false).asInstanceOf[Tree]
      }
      catch{
        case _: StackOverflowError => typeContext.error(typeContext.enclosingPosition, "Ran into stack overflow loop, this is probably due to a known bug (#7178) of Scala < 2.13 when using recursive references inside auxiliary constructors, you might fix this by moving a recursive call (e.g. Tools.resolveIp(Service) inside Service object) outside of the constructor (probably a multitier.start call), declare it as val there and reference it, at: " + x.toString); x.asInstanceOf[Tree]
        case _: typeContext.TypecheckException => x.asInstanceOf[Tree]
      }
    }
    def tpeType(x : Tree) : Type = tpe(x).tpe.asInstanceOf[typeContext.Type]//orElse NoType
    @deprecated("1.0") def eval[T](tree : Tree) : Option[T] = {
      Some(tree.asInstanceOf[T]) //todo evaluation is not going anywhere, because of dynamics.
      /*try{
        Some(typeContext.eval(typeContext.Expr[T](typeContext.untypecheck(tree))))
      }
      catch{
        case _ @ _ => None
      }*/
    }
  }

  private[loci] final def getIpString(s : String) : String = {
    var ip = s.toLowerCase.replaceAll("\\$|\\.", "_")
    while(ip.takeRight(1).equals("_")) ip = ip.dropRight(1)
    ip
  }
}
