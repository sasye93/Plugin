/**
  * Implementation of the @service annotation.
  * If an object is annotated with @service, it will be turned into an image / container / service.
  * Inside the @service object, multitier.start must be called on a peer declared inside a @containerize annotated object.
  * @author Simon Schönwälder
  * @version 1.0
  */
package loci.container

import loci.container.build.IO._
import loci.container.build.Options

import scala.annotation.{StaticAnnotation, compileTimeOnly}
import scala.language.experimental.macros
import scala.reflect.macros.blackbox
import scala.language.implicitConversions

/**
  * @param config Optional config passed to this macro, @see loci.container.impl.types.ContainerConfig
  */
@compileTimeOnly("enable macro paradise to expand macro annotations")
class service(config: String = "") extends StaticAnnotation {
  def macroTransform(annottees: Any*) : Any = macro loci.container.ServiceImpl.impl
}

object ServiceImpl {

  def impl(c : blackbox.Context)(annottees: c.Expr[Any]*): c.Expr[Any] = {

    //todo duplicate
    val tc = new Tools.TypeConverter(c)
    import c.universe._
    import tc.{tpe,tpeType}

    implicit def convertCtoTC(t : c.Tree) : tc.typeContext.Tree = t.asInstanceOf[tc.typeContext.Tree]
    implicit def convertTCtoC(t : tc.typeContext.Tree) : c.Tree = t.asInstanceOf[c.Tree]
    implicit def convertCtoTyC(t : c.Type) : tc.typeContext.Type = t.asInstanceOf[tc.typeContext.Type]
    implicit def convertTyCtoC(t : tc.typeContext.Type) : c.Type = t.asInstanceOf[c.Type]

    val resolveIpSig = tpe(reify(Tools.resolveIp _).tree).find(_.isInstanceOf[Select]).head.symbol.fullName
    val publicIpSig = tpe(reify(Tools.publicIp _).tree).find(_.isInstanceOf[Select]).head.symbol.fullName
    val localhostSig =  tpe(reify(Tools.localhost _).tree).find(_.isInstanceOf[Select]).head.symbol.fullName

    annottees.map(_.tree).toList match {
      case (module : ModuleDef) :: Nil =>
        val mod = c.typecheck(module).asInstanceOf[ModuleDef] //todo note that this fails if declarations are made outside, make try catch
        /**
         * extract macro argument.
         */
        def eq(p : String) : Boolean = p.equalsIgnoreCase("service") || p.equalsIgnoreCase("gateway")
        val config : Option[String] = c.prefix.tree match {
            case q"new $s(config=$p)" if eq(s.toString) && p.toString.matches("^\"(.|\n)+\"") => Some(p.toString.stripPrefix("\"").stripSuffix("\""))
            case q"new $s($p)" if eq(s.toString) && p.toString.matches("^\"(.|\n)+\"") => Some(p.toString.stripPrefix("\"").stripSuffix("\""))
            case q"new $s($p)" if eq(s.toString) => if(p.nonEmpty) c.warning(c.enclosingPosition, s"Did not recognize config provided, : $p"); None
            case q"new $_" => None
            case _ => c.abort(c.enclosingPosition, "Invalid @service annotation style. Use '@service(path : String = \"\"), e.g. @service(\"scripts/mycfg.xml\") or without parameter.")
          }

        /**
          * Was this redirected from @gateway impl, and is a gateway?
          */
        val isGateway : Boolean = c.prefix.tree match {
          case q"new $s" => s.toString.startsWith("gateway")
          case _ => false
        }

        val containerEntryClass: String = mod.symbol.fullName

        c.info(c.enclosingPosition, s"Found and processing @${ if(isGateway) "gateway" else "service" }: ${containerEntryClass}.", true)

        val allEndPoints = mod.impl.body.flatMap(_.collect{
          case a @ Apply(_, args)
            if{
              val result = scala.util.Try{ tpe(a).symbol.asMethod.fullName }
              result.isSuccess && result.get == typeOf[loci.runtime.Runtime[Any]].erasure.companion.decl(TermName("start")).fullName
            } =>

            def getNormalizedName(s : Select) : String = { //todo this is somehow ... with $loci$peer$sig$
              s.qualifier.symbol.fullName + "." + s.name.toString.split('$').last
            }

            val containerPeerClass: String = args.collectFirst({
              case s @ Select(_, TermName(_)) if s.tpe <:< c.typeOf[loci.runtime.Peer.Signature] => getNormalizedName(s)
            }).getOrElse("")

            val endPoints : List[SimplifiedConnectionEndPoint] = args.foldLeft(List[SimplifiedConnectionEndPoint]())((B, a) => B ++ a.filter({
              case a : Apply => (tpeType(a) weak_<:< c.typeOf[loci.language.Connections]) && !a.exists({ case Select(_, TermName("and")) => true; case _ => false })
              case _ => false
            }).collect(
                  {
                        case a2: Apply => a2.collect({
                          case _a@Apply(_, ___args) if tpeType(_a).erasure weak_<:< c.weakTypeOf[loci.communicator.ConnectionSetup[loci.communicator.ProtocolCommon]].erasure =>
                            val way = a2.fun match {
                              case s: Select => if(a2.args.exists(_.exists({ case Select(_,TermName("firstConnection")) => true; case _ => false}))) "both" else tpe(s).symbol.name.toString
                              case _ => "both"
                            }
                            val conPeer = a2.args match {
                              case (s : Select) :: _ => getNormalizedName(s)
                              case _ => "unknown"
                            }
                            def hostWarning() : Unit = c.warning(c.enclosingPosition, "Services will not be reachable if they listen on localhost, or if they try to connect to localhost. Use Tools.publicIp or '0.0.0.0' in a listen statement, and Tools.resolveIp(entryPoint : @service/@gateway object) when connecting.")
                            SimplifiedConnectionEndPoint(
                              conPeer,
                              ___args.collectFirst({
                                case Literal(Constant(port: Int)) => Some(port)
                                case s : Apply if(tpe(s).tpe.resultType weak_<:< c.typeOf[Long]) => None
                              }).flatten.getOrElse(0),
                              {
                                val host = ___args.collectFirst({
                                  case Literal(Constant(host: String)) => Some(host)
                                  case _ : Select => None
                                  case a : Apply => tpe(a).symbol.fullName match{
                                    case `resolveIpSig` => Some(a.args.head.symbol.fullName)
                                    case `publicIpSig` => Some("0.0.0.0")
                                    case `localhostSig` => Some("127.0.0.1")
                                    case _ => None
                                  }
                                  case _ => None
                                }).flatten.getOrElse("unknown")
                                if(host == "" || host == "localhost" || host == "127.0.0.1") hostWarning()
                                host
                              },
                              way,
                              "1.0", //version is currently not in use.
                              tpeType(_a).typeArgs.headOption match {
                                case Some(prot) => prot match{
                                  //Recognition based on types is disabled because it requires the dependency only for this, which blows up jar size. Workaround used.
                                  //case p if p <:< c.typeOf[loci.communicator.tcp.TCP] => "TCP"
                                  //case prot if prot <:< c.typeOf[loci.communicator.ws.akka.WS] => "WebSocket"
                                  case p if p.typeSymbol.fullName.contains("TCP") => "TCP"
                                  case p if p.typeSymbol.fullName.contains("WS") => "WebSockets"
                                  case _ => "unknown"
                                }
                                case _ => "unknown"
                              }
                            )
                        })
                      }).flatten)
            (containerPeerClass, endPoints)
        })
        if(allEndPoints.nonEmpty){
          if(allEndPoints.length > 1) c.warning(c.enclosingPosition, s"You have multiple start directives inside your @service/@gateway object, which is strongly discouraged for Microservices. You should create an own service for each peer you start.")

          if(containerEntryClass.length > 63)
            c.error(c.enclosingPosition, s"The projected image/service name of ${ containerEntryClass } is longer than 63 characters. Docker cannot handle such long image and service names. Please slim down package structure or shorten module and object names. ")
          else if(containerEntryClass.length > 53)
            c.warning(c.enclosingPosition, s"The projected image/service name of ${ containerEntryClass } is longer than 53 characters, and might hit the 63 character limit if you enable a localDb whose name will build upon this. Docker cannot handle image and service names with more than 63 characters. Please slim down package structure or shorten module and object names. ")

          /**
            * pickle the service info, so it can be used by the build stage.
            */
          val endpoints = allEndPoints.flatMap(_._2)
          val endpoint = SimplifiedContainerEntryPoint(containerEntryClass, allEndPoints.head._1, config, endpoints, isGateway)

          if(containerEntryClass.split('.').last.equalsIgnoreCase("globaldb")) c.warning(c.enclosingPosition, "The service name 'database' is reserved for automatically set up databases using the globalDb option, you should not use this as a service name, as this could result in conflicts.")
          if(isGateway && endpoints.forall(_.way == "connect") && !(config.isDefined && config.get.contains("ports"))) c.warning(c.enclosingPosition, "Seems like you don't listen for any connections within this service, consider annotating it with @service instead of @gateway.")

          implicit val logger : Logger = new Logger(c.universe.asInstanceOf[tools.nsc.Global].reporter)
          val io = new IO()
          io.createDir(Options.tempDir)
          if(!containerEntryClass.contentEquals(NoSymbol.fullName))
            io.serialize(endpoint, Options.tempDir.resolve(containerEntryClass + ".ep"))
        }
        else{
          c.warning(c.enclosingPosition, "Couldn't find a multitier.start directive inside @service/@gateway object. It is either non-existent or cannot be detected by the containerization extension. Make sure you use compatible syntax. Service will be skipped and not deployed.")
        }

        c.Expr[Any](module)
      case _ => c.abort(c.enclosingPosition, "Invalid annotation: @loci.service must prepend a module object (object or case object).")
    }
  }
}