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
    import tc._

    //todo dupl, make it somehow different
    implicit def convertCtoTC(t : c.Tree) : tc.typeContext.Tree = t.asInstanceOf[tc.typeContext.Tree]
    implicit def convertTCtoC(t : tc.typeContext.Tree) : c.Tree = t.asInstanceOf[c.Tree]
    implicit def convertCtoTyC(t : c.Type) : tc.typeContext.Type = t.asInstanceOf[tc.typeContext.Type]
    implicit def convertTyCtoC(t : tc.typeContext.Type) : c.Type = t.asInstanceOf[c.Type]
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
        /**
         * extract macro arguments.
         */
        def eq(p : String) : Boolean = (p.equalsIgnoreCase("service") || p.equalsIgnoreCase("gateway"))
        val config : Option[String] = (c.prefix.tree match {
            case q"new $s(path=$p)" if eq(s.toString) && p.toString.matches("^\".*\"$") => Some(p.toString.replaceAll("\"", ""))
            case q"new $s($p)" if eq(s.toString) && p.toString.matches("^\".+\"$") => Some(p.toString.replaceAll("\"", ""))
            case q"new $s($p)" if eq(s.toString) && p.toString.isEmpty => None
            case q"new $s" => None
            case _ => c.abort(c.enclosingPosition, "Invalid @service annotation style. Use '@service(path : String = \"\"), e.g. @service(\"scripts/mycfg.xml\") or without parameter.")
          })

        val isGateway : Boolean = (c.prefix.tree match {
          case q"new $s" => s.toString.startsWith("gateway")
          case _ => false
        })

        m.impl.body.foreach(_.find( x =>
          scala.util.Try{(tpe(x).symbol != null || tpe(x).tpe != null)}.getOrElse(false)
        ).foreach(x => c.info(c.enclosingPosition, (tpe(x).symbol + ":" + tpe(x).tpe).toString(), true)))
        m.impl.body.map(_.collect{
          case a @ Apply(f, args)
            if{
                c.info(c.enclosingPosition, ":" + showRaw(a),true)
                f match{
                  case Select(qual, TermName(methodName)) =>
                    val result = scala.util.Try{ tpe(qual).tpe.typeSymbol.fullName + "." + methodName }
                    result.isSuccess && result.get == (typeOf[loci.multitier.type].decls.find(d => d.isMethod && d.asMethod.name.toString == "start").getOrElse(NoSymbol).asTerm.fullName)
                  case _ => false
                }
            } =>
            val containerEntryClass: String = tpe(m).symbol.fullName;
            var containerPeerClass: String = ""
            c.info(c.enclosingPosition, "aainstac : " + Options.tempDir, true);

            val endPoints : List[SimplifiedConnectionEndPoint] = args.foldLeft(List[SimplifiedConnectionEndPoint]())((B, a) => B ++ a.collect({
                case a@Apply(_f, _args)
                  if (
                    ((tp : c.Tree) => {
                      (tp.tpe != null && (tp.tpe.erasure weak_<:< c.weakTypeOf[loci.Instance[Any]].erasure)) ||
                        (_f.isInstanceOf[AppliedTypeTree] && tpe(New(_f)).tpe != null && (tpe(New(_f)).tpe.erasure weak_<:< c.weakTypeOf[loci.Instance[Any]].erasure))
                    })(tpe(a))
                    ) =>
                  {
                    containerPeerClass = scala.util.Try{
                      val _peerClass = tpeType(if (_f.isInstanceOf[AppliedTypeTree]) New(_f) else a)
                      if(_peerClass != null)
                        _peerClass.typeArgs.headOption.getOrElse(NoType).typeSymbol.fullName
                      else containerPeerClass
                    }.getOrElse(containerPeerClass)
                    _f match{
                      case t @ AppliedTypeTree(a,b) => tpe(t)
                        c.info(c.enclosingPosition, "ttt0ta : " + (tpe(New(t)).tpe), true);
                      case _ =>
                    }

                    _args.flatMap(_.collect({
                      case a : Apply if (tpeType(a).resultType =:= c.typeOf[loci.language.Connections] && !a.exists({ case Select(_, TermName("and")) => true; case _ => false })) => a
                    }).flatMap{ a => //these are single connections, and they include the other peer!
                      c.warning(c.enclosingPosition, "A: " + showRaw(a))
                      //todo were here!!! currently only works for single connection.
                      //todo: is connect,listen,connectfirst the only methods?
                      val ap = a.asInstanceOf[Apply]
                      ap.collect({
                        case a2: Apply if (tpeType(a2).resultType =:= c.typeOf[loci.language.Connections]) => a2.collect({
                          case _a@Apply(___fun, ___args) if tpeType(_a).erasure weak_<:< c.weakTypeOf[loci.communicator.ConnectionSetup[loci.communicator.ProtocolCommon]].erasure => c.info(c.enclosingPosition, "that app" + showRaw(a2), true);
                            val (way, conPeer) = a2.fun match {
                              case t: TypeApply =>
                                (if(a2.args.exists(_.exists({ case Select(_,TermName("firstConnection")) => true; case _ => false}))) "both" else tpe(t.fun).toString, tpe(t.args.head).toString)
                              case _ => ("both", "unknown")
                              //todo problem is that connect can also listen with connectfirst.
                              //todo currently, cons are added multiple times bec. of schachtelung
                            }
                            c.info(c.enclosingPosition, "way : " + way, true);

                            def hostWarning() : Unit = c.warning(c.enclosingPosition, "Services will not be reachable if they listen on localhost, or if they try to connect to localhost. Use Tools.publicIp or '0.0.0.0' in a listen statement, and Tools.resolveIp(entryPoint : @service/@gateway object) when connecting.")
                            SimplifiedConnectionEndPoint(
                              conPeer,
                              (___args.collectFirst({
                                case Literal(Constant(port: Int)) => port
                              }).getOrElse(0).toInt),
                              ___args.collectFirst({
                                case Literal(Constant(host: String)) => if(host == "localhost" || host == "127.0.0.1") hostWarning(); host //todo
                              }).getOrElse({ hostWarning(); "localhost" }), //todo
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
                                case Some(prot) if (prot <:< c.typeOf[loci.communicator.tcp.TCP]) => "TCP"
                                //else if (prot <:< c.typeOf[loci.communicator.ws.akka.WebSocketListener]) "WebSocket"
                                case _ => "unknown"
                              }
                            )
                        })
                      })
                    }.flatten)

                    //body // :+ reify{ SimplifiedContainerEntryPoint(containerEntryClass, containerPeerClass, null, null, endPoints) } // todo config and script, refactor these classes to be applieable here, is hack now (bec direct use of constrcuctrro).
                    //c.abort(c.enclosingPosition, SimplifiedContainerEntryPoint(containerEntryClass, containerPeerClass, Paths.get(config).toFile, null, endPoints).toString)
                  }
              }).flatten)
            /**
             * save it.
             *  */
            c.info(c.enclosingPosition, "ep : " + SimplifiedContainerEntryPoint(containerEntryClass, containerPeerClass, config, endPoints, isGateway), true);

            if(isGateway && endPoints.forall(_.way == "connect")) c.warning(c.enclosingPosition, "Seems like you don't listen for any connections within this service, consider annotating it with @service instead of @gateway.")

            implicit val logger : Logger = new Logger(c.universe.asInstanceOf[tools.nsc.Global].reporter)
            val io = new IO()
            io.createDir(Options.tempDir)
            io.serialize(SimplifiedContainerEntryPoint(containerEntryClass, containerPeerClass, config, endPoints, isGateway), Options.tempDir.resolve(containerEntryClass + ".ep"))
        })
        c.Expr[Any](m)
      case _ => c.abort(c.enclosingPosition, "Invalid annotation: @loci.service must prepend a module object (object or case object).")
    }

  }
}
