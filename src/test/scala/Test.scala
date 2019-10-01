/*package thesis.samples

import loci._
import loci.transmitter.rescala._
import loci.serializer.upickle._
import loci.communicator.tcp._
import rescala.default._
import java.util.{Calendar, Date}
import java.text.SimpleDateFormat

import loci.communicator.ws.akka._
import akka.http.scaladsl.model.ContentType
import akka.http.scaladsl.model.MediaTypes._
import akka.http.scaladsl.model.HttpCharsets._
import akka.http.scaladsl.server.Directives._
import loci.container.Tools._
import org.mongodb.scala._
import loci.container._


/**
 * Public Api
 */
@multitier protected trait Api{
  /**
   * Services
   */
  @peer type Peer2
  @peer type Client2 <: Peer2
  @peer type Server2 <: Peer2

  lazy final val version = 1.0
  /**
   * Api provided by Server Service
   */
  val time : Var[Long] on Server2
}
/**
 * Client implementation
 */
@multitier protected[thesis] trait ClientImpl extends Api {
  @peer type Client2 <: Peer2 { type Tie <: Single[Server2] }

  on[Client2] { implicit! =>
    val display = Signal { (new SimpleDateFormat("hh:mm:ss")) format new Date(time.asLocal()) }
    display.changed observe println
  }
}
/**
 * Service implementation
 */
@multitier protected[thesis] trait ServerImpl extends Api {
  @peer type Server <: Peer2 { type Tie <: Multiple[Client2] }
  val time: Var[Long] on Server2 = on[Server2] { implicit! => Var(0L) }

  def db() : Unit={
  }
  def main() : Unit on Server2 = placed{ implicit! =>
    //db()
    while (true) {
      time set Calendar.getInstance.getTimeInMillis
      Thread sleep 1000
    }
  }
}
@multitier @containerize object MultitierApi extends ServerImpl with ClientImpl

@service("C:\\Users\\Simon S\\Dropbox\\Masterarbeit\\Code\\examplesScalaLoci\\config.json")
object Service extends App {
  // Use a Connection String
  val mongoClient: MongoClient = MongoClient(Tools.globalDbIp(MultitierApi))
  val mongoClient2: MongoClient = MongoClient(Tools.localDbIp(this))

  val database: MongoDatabase = mongoClient.getDatabase("mydb")
  val database2: MongoDatabase = mongoClient2.getDatabase("mydb")
  database.createCollection("global")
  database2.createCollection("local")
  val collection = database.getCollection("global")
  val collection2 = database2.getCollection("local")
  val doc: Document = Document("_id" -> 0, "name" -> "MongoDB", "type" -> "database",
    "count" -> 1, "info" -> Document("x" -> 203, "y" -> 102))
  println(Tools.globalDbIp(MultitierApi))
  println(Tools.localDbIp(this))
  collection.insertOne(doc).subscribe(new Observer[Completed] {

    override def onNext(result: Completed): Unit = println("Inserted")

    override def onError(e: Throwable): Unit = println("Failed")

    override def onComplete(): Unit = println("Completed")
  })
  collection2.insertOne(doc).subscribe(new Observer[Completed] {

    override def onNext(result: Completed): Unit = println("Inserted")

    override def onError(e: Throwable): Unit = println("Failed")

    override def onComplete(): Unit = println("Completed")
  })
  loci.multitier start new Instance[MultitierApi.Server2](
    listen[MultitierApi.Client2] { WS(2, Tools.publicIp)  }
  )
}
@service(
  """{
    |  "replicas": 2
    |}"""
)
object Client extends App {

  Tools.resolveIp(Service)
  val t = Tools.resolveIp(Service)
  final def g = 9
  loci.multitier start new Instance[MultitierApi.Client2](
    connect[MultitierApi.Server2] {  WS(s"ws://${ Tools.resolveIp(Service) }:2") }
  ){}
}
@gateway
object Peer extends App {
  loci.multitier start new Instance[MultitierApi.Peer2](
    listen[MultitierApi.Peer2] { TCP(3, Tools.publicIp) }
  )
}*/