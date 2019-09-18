import org.scalatest.FunSuite

import loci._
import loci.transmitter.rescala._
import loci.serializer.upickle._
import loci.communicator.tcp._
import rescala.default._
import java.util.{Calendar, Date}
import java.text.SimpleDateFormat

import loci.container._

/**
 * Public Api
 */
@multitier trait Api{
  /**
   * Services
   */
  @peer type Peer
  @peer type Client <: Peer
  @peer type Server <: Peer

  lazy final val version = 1.0
  /**
   * Api provided by Server Service
   */
  val time : Var[Long] on Server
}
/**
 * Client implementation
 */
@multitier trait ClientImpl extends Api {
  @peer type Client <: Peer { type Tie <: Single[Server] }

  on[Client] { implicit! =>
    val display = Signal { (new SimpleDateFormat("hh:mm:ss")) format new Date(time.asLocal()) }
    display.changed observe println
  }
}
/**
 * Service implementation
 */
@multitier trait ServerImpl extends Api {
  @peer type Server <: Peer { type Tie <: Multiple[Client] }
  val time: Var[Long] on Server = on[Server] { implicit! => Var(0L) }

  def main() : Unit on Server = {
    while (true) {
      time set Calendar.getInstance.getTimeInMillis
      Thread sleep 1000
    }
  }
}
@multitier @containerize object MultitierApi extends ServerImpl with ClientImpl

@service
object Server extends App {
  loci.multitier start new Instance[MultitierApi.Server](
    listen[MultitierApi.Client] { TCP(43059) }
  )
}
@service
object Client extends App {
  loci.multitier start new Instance[MultitierApi.Client](
    connect[MultitierApi.Server] { TCP("localhost", 1) } and
      connect[MultitierApi.Client] { TCP("localhost", 2) } and
      connect[MultitierApi.Peer] { TCP(3, "localhost").firstConnection }
  )
}
@service object Peer extends App {
  val ac = 1
  def test = "J"
  loci.multitier start new Instance[MultitierApi.Peer](
    listen[MultitierApi.Peer] { TCP(431) } and
    connect[MultitierApi.Peer] { TCP("localhost", 123) }
  ){
    def test = 1
  }
  val j = 4
}

class Test extends FunSuite {
  test("CubeCalculator.cube") {

    assert(1 == 1)
  }
}