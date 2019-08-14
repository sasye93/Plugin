package loci.containerize

package object Check {
  def ?(testForNull : Any) : Boolean = testForNull != null
  def ?(testForEmpty : String) : Boolean = testForEmpty != null && testForEmpty.length > 0

  def !(testForNull : Any) : Boolean = testForNull == null

  def ?#(testForNull : Any, default : Any) : Any = if (Check ? testForNull) default else testForNull
  def ?=>[T](testForNull : Any, fun : => T, default : T = null) : T = if(Check ? testForNull) fun else default
}