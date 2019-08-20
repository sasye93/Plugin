package loci.containerize

package object Check {
  def !(testForNull : Any) : Boolean = testForNull == null
  def ?(testForNull : Any) : Boolean = !(this ! testForNull)

  def ??(testForEmpty : String) : Boolean = testForEmpty != null && !testForEmpty.isEmpty
  def ??(testForEmpty : String, _then : String, otherwise : String = "") : String = if(this ?? testForEmpty) _then else otherwise

  def ?=>[T](testForNull : Any, fun : => T, default : T = null) : T = if(this ? testForNull) fun else default
}