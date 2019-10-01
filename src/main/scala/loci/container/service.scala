package loci.container

import java.io.File
import java.nio.file.{Path, Paths}
import java.time.LocalDateTime

import loci.Instance
import loci.container._
import loci.containerize.IO.Logger
import loci.containerize.types.{SimplifiedConnectionEndPoint, SimplifiedContainerEntryPoint, SimplifiedContainerModule}

import scala.annotation.{StaticAnnotation, compileTimeOnly}
import scala.language.experimental.macros
import scala.reflect.macros.blackbox
import scala.tools.reflect.ToolBox
import scala.reflect.runtime
import loci.container.Tools
import loci.container.Tools.getIpString
import loci.containerize.Options
import loci.containerize.IO.IO

@compileTimeOnly("enable macro paradise to expand macro annotations")
class service(config: String = "") extends StaticAnnotation {
  def macroTransform(annottees: Any*) : Any = macro loci.container.ServiceImpl.impl
}

object ServiceImpl {

  def impl(c : blackbox.Context)(annottees: c.Expr[Any]*): c.Expr[Any] = {

    import c.universe._
    //todo dupl, make it somehow different
    val tc = new Tools.TypeConverter(c)
    import c.universe._
    import tc.{tpe,tpeType,eval}

    //todo dupl, make it somehow different
    implicit def convertCtoTC(t : c.Tree) : tc.typeContext.Tree = t.asInstanceOf[tc.typeContext.Tree]
    implicit def convertTCtoC(t : tc.typeContext.Tree) : c.Tree = t.asInstanceOf[c.Tree]
    implicit def convertCtoTyC(t : c.Type) : tc.typeContext.Type = t.asInstanceOf[tc.typeContext.Type]
    implicit def convertTyCtoC(t : tc.typeContext.Type) : c.Type = t.asInstanceOf[c.Type]

    val resolveIpSig = (tpe(reify(Tools.resolveIp _).tree).find(_.isInstanceOf[Select]).head).symbol.fullName
    val localhostSig =  (tpe(reify(Tools.localhost _).tree).find(_.isInstanceOf[Select]).head).symbol.fullName

    //todo prevent null pointer
    //def tpeType(x : Tree) : Type = tpe(x).asInstanceOf[c.Tree].tpe.asInstanceOf[c.Type]//orElse NoType

      /**
    entryClassSymbolString : String,
    peerClassSymbolString : String,
    config : File,
    setupScript : File,
    endPoints : List[SimplifiedConnectionEndPoint]
    )
    case class SimplifiedConnectionEndPoint(
                                             connectionPeerSymbolString : String,
                                             port : Integer,
                                             host : String,
                                             way : String,
                                             version : String
                                           )*/

    //todo this will only hit for module, class?
    annottees.map(_.tree).toList match {
      case (m : ModuleDef) :: Nil =>
        val mod = c.typecheck(m).asInstanceOf[ModuleDef] //todo note that this fails if declarations are made outside
        /**
         * extract macro arguments.
         */
        def eq(p : String) : Boolean = (p.equalsIgnoreCase("service") || p.equalsIgnoreCase("gateway"))
        val config : Option[String] = (c.prefix.tree match {
            case q"new $s(config=$p)" if eq(s.toString) && p.toString.matches("^\"(.|\n)+\"") => c.info(c.enclosingPosition, "1 : " + p.toString(), true);Some(p.toString.stripPrefix("\"").stripSuffix("\""))
            case q"new $s($p)" if eq(s.toString) && p.toString.matches("^\"(.|\n)+\"") => c.info(c.enclosingPosition, "2 : " + p.toString(), true);Some(p.toString.stripPrefix("\"").stripSuffix("\""))
            case q"new $s($p)" if eq(s.toString) => if(p.nonEmpty) c.warning(c.enclosingPosition, s"Did not recognize config provided, : $p"); None
            case q"new $s" => None
            case _ => c.abort(c.enclosingPosition, "Invalid @service annotation style. Use '@service(path : String = \"\"), e.g. @service(\"scripts/mycfg.xml\") or without parameter.")
          })

        val isGateway : Boolean = (c.prefix.tree match {
          case q"new $s" => s.toString.startsWith("gateway")
          case _ => false
        })

        /*
        m.impl.body.foreach(_.find( x =>
          scala.util.Try{ (tpe(x) != null && (tpe(x).symbol != null || tpe(x).tpe != null)) }.getOrElse(false)
        ).foreach(x => c.info(c.enclosingPosition, (tpe(x).symbol + ":" + tpe(x).tpe).toString(), true)))
         */

        c.info(c.enclosingPosition, showRaw(mod.impl.body), true);
        val starts = mod.impl.body.flatMap(_.collect{
          case a @ Apply(f, args)
            if{
              val result = scala.util.Try{ tpe(a).symbol.asMethod.fullName }
              result.isSuccess && result.get == (typeOf[loci.runtime.Runtime[Any]].erasure.companion.decl(TermName("start")).fullName)
                /*f match{
                  case Select(qual, TermName(methodName)) =>
                    val result = scala.util.Try{ tpe(a).tpe.erasure }
                    c.info(c.enclosingPosition, showRaw(a) + ":" + result + ":" + (typeOf[loci.Runtime[Any]].erasure), true);
                    result.isSuccess && result.get =:= (typeOf[loci.Runtime[Any]].erasure)
                  case _ => false
                }*/
            } =>

            def getNormalizedName(s : Select) : String = { //todo this is somehow ... with $loci$peer$sig$
              s.qualifier.symbol.fullName + "." + (s.name.toString.split('$').last)
            }

            var containerEntryClass: String = mod.symbol.fullName
            c.info(c.enclosingPosition, "s : " + showRaw(c.untypecheck(a).asInstanceOf[Apply]), true);
            var containerPeerClass: String = args.collectFirst({
              case s @ Select(qual, TermName(methodName)) if s.tpe <:< c.typeOf[loci.runtime.Peer.Signature] => getNormalizedName(s)
            }).getOrElse("")

            c.info(c.enclosingPosition, "aainstac : " + containerPeerClass, true);

            c.info(c.enclosingPosition, "apply : " + showRaw(a.symbol.owner), true);
            val endPoints : List[SimplifiedConnectionEndPoint] = args.foldLeft(List[SimplifiedConnectionEndPoint]())((B, a) => B ++ a.filter({
              case a@Apply(_f, _args)
              => ((tpeType(a) weak_<:< c.typeOf[loci.language.Connections]) && !a.exists({ case Select(_, TermName("and")) => true; case _ => false }))
              case _ => false
            }).collect(
                  {
                    /*
                    containerPeerClass = scala.util.Try{
                      val _peerClass = tpeType(if (_f.isInstanceOf[AppliedTypeTree]) New(_f) else a)
                      if(_peerClass != null)
                        _peerClass.typeArgs.headOption.getOrElse(NoType).typeSymbol.fullName
                      else containerPeerClass
                    }.getOrElse(containerPeerClass)
                    */
                    /*
                    _args.flatMap(_.collect({
                      case a : Apply if (tpeType(a).resultType =:= c.typeOf[loci.language.Connections] && !a.exists({ case Select(_, TermName("and")) => true; case _ => false })) => a
                    }).flatMap{ a => *///these are single connections, and they include the other peer!
                      /*c.warning(c.enclosingPosition, "A: " + showRaw(a))
                      //todo were here!!! currently only works for single connection.
                      //todo: is connect,listen,connectfirst the only methods?
                      val ap = a.asInstanceOf[Apply]
                      ap.collect({*/
                        case a2: Apply => a2.collect({
                          case _a@Apply(___fun, ___args) if tpeType(_a).erasure weak_<:< c.weakTypeOf[loci.communicator.ConnectionSetup[loci.communicator.ProtocolCommon]].erasure => c.info(c.enclosingPosition, "that app" + showRaw(a2), true);
                            val way = a2.fun match {
                              case s: Select => if(a2.args.exists(_.exists({ case Select(_,TermName("firstConnection")) => true; case _ => false}))) "both" else tpe(s).symbol.name.toString
                              case _ => "both"
                              //todo problem is that connect can also listen with connectfirst.
                              //todo currently, cons are added multiple times bec. of schachtelung
                            }
                            val conPeer = a2.args match {
                              case (s : Select) :: _ => getNormalizedName(s)
                              case _ => "unknown"
                            }
                            c.info(c.enclosingPosition, "a : " + c.typeOf[loci.peer].typeSymbol.typeSignature, true);
                            def hostWarning() : Unit = c.warning(c.enclosingPosition, "Services will not be reachable if they listen on localhost, or if they try to connect to localhost. Use Tools.publicIp or '0.0.0.0' in a listen statement, and Tools.resolveIp(entryPoint : @service/@gateway object) when connecting.")
                            SimplifiedConnectionEndPoint(
                              conPeer,
                              (___args.collectFirst({
                                case Literal(Constant(port: Int)) => Some(port)
                                case s : Apply if(tpe(s).tpe.resultType weak_<:< c.typeOf[Long]) => None //todo this ok?
                              }).flatten.getOrElse(0)),
                              {
                                val host = ___args.collectFirst({
                                  case Literal(Constant(host: String)) => Some(host)
                                  case s : Select => eval[String](s)
                                  case a : Apply =>
                                    eval[String](a).orElse(a match{
                                      case ap : Apply => tpe(ap).symbol.fullName match{
                                        case `resolveIpSig` => Some(ap.args.head.symbol.fullName)
                                        case `localhostSig` => Some(Tools.localhost)
                                        case _ => None
                                      }
                                      case _ => None
                                    })
                                }).flatten.getOrElse(Tools.localhost)
                                if(host == "" || host == "localhost" || host == "127.0.0.1") hostWarning()
                                host
                              }, //todo
                              way,
                              "1.0",
                              /*{
                                  val tp = tpeType(a)
                                  c.info(c.enclosingPosition, tp.toString, true);
                                  if (tp weak_<:< c.weakTypeOf[loci.language.Connections])
                                    tp.typeArgs.headOption.getOrElse(NoType).typeSymbol.fullName
                                  else "unknown"
                                },*/

                              tpeType(_a).typeArgs.headOption match {
                                case Some(prot) => prot match{
                                  case prot if (prot <:< c.typeOf[loci.communicator.tcp.TCP]) => "TCP"
                                  case prot if (prot <:< c.typeOf[loci.communicator.ws.akka.WS]) => c.info(null, "a :asd2 " + prot, true);"WebSocket"
                                  case _ => "unknown"
                                }
                                case _ => "unknown"
                              }
                            )
                        })
                      })
                    /*}*/.flatten)

                    //body // :+ reify{ SimplifiedContainerEntryPoint(containerEntryClass, containerPeerClass, null, null, endPoints) } // todo config and script, refactor these classes to be applieable here, is hack now (bec direct use of constrcuctrro).
                    //c.abort(c.enclosingPosition, SimplifiedContainerEntryPoint(containerEntryClass, containerPeerClass, Paths.get(config).toFile, null, endPoints).toString)

            /**
             * save it.
             *  */
            val endpoint = SimplifiedContainerEntryPoint(containerEntryClass, containerPeerClass, config, endPoints, isGateway)
            c.info(c.enclosingPosition, "ep : " + containerEntryClass + endpoint, true);

            if(containerEntryClass.split('.').last.equalsIgnoreCase("globaldb")) c.warning(c.enclosingPosition, "The service name 'database' is reserved for automatically set up databases using the globalDb option, you should not use this as a service name, as this could result in conflicts.")
            if(isGateway && endPoints.forall(_.way == "connect")) c.warning(c.enclosingPosition, "Seems like you don't listen for any connections within this service, consider annotating it with @service instead of @gateway.")

            implicit val logger : Logger = new Logger(c.universe.asInstanceOf[tools.nsc.Global].reporter)
            val io = new IO()
            io.createDir(Options.tempDir)
            //if(!containerEntryClass.contentEquals(NoSymbol.fullName)) todo active
              io.serialize(endpoint, Options.tempDir.resolve(containerEntryClass + ".ep"))
            endpoint
        })
        if(starts.length > 1) c.warning(c.enclosingPosition, s"You have multiple start directives inside your @service/@gateway object, which is strongly discouraged for a Microservice.")
        c.info(null, starts.length.toString, true)
        c.Expr[Any](m)
      case _ => c.abort(c.enclosingPosition, "Invalid annotation: @loci.service must prepend a module object (object or case object).")
    }
  }
}
