package loci.container

package object Tools {
  private[loci] def getIpString(s : => String) : String = {
    var ip = s.toLowerCase.replaceAll("\\$|\\.", "_")
    while(ip.takeRight(1).equals("_")) ip = ip.dropRight(1)
    ip
  }
  def resolveIp(c : => Any) : String = getIpString(c.getClass.getTypeName)
  def publicIp() : String = "0.0.0.0" //todo: doc only necessary when not standalone containers running, bec than we can iptabling
}
