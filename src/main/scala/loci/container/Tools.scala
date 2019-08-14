package loci.container

package object Tools {
  private[loci] def getIpString(s : => String) : String = {
    var ip = s.toLowerCase.replace("$", ".")
    while(ip.takeRight(1).equals(".")) ip = ip.dropRight(1)
    ip
  }
  def resolveIp(c : => Any) : String = getIpString(c.getClass.getTypeName)
}
