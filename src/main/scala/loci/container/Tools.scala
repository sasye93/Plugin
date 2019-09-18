package loci.container

package object Tools {
  def resolveIp(c : => Any) : String = getIpString(c.getClass.getTypeName)
  def publicIp() : String = "0.0.0.0" //todo: doc only necessary when not standalone containers running, bec than we can iptabling

  private[container] class TypeConverter(val typeContext : scala.reflect.macros.blackbox.Context){
    import typeContext._

    def tpe(x : Tree) : Tree = {
      try{
        typeContext.typecheck(x.asInstanceOf[typeContext.Tree]).asInstanceOf[Tree]
      }
      catch{
        case _: typeContext.TypecheckException => x.asInstanceOf[Tree]
      }
    }
    def tpeType(x : Tree) : Type = tpe(x).tpe.asInstanceOf[typeContext.Type]//orElse NoType
  }

  private[loci] def getIpString(s : => String) : String = {
    var ip = s.toLowerCase.replaceAll("\\$|\\.", "_")
    while(ip.takeRight(1).equals("_")) ip = ip.dropRight(1)
    ip
  }
}
