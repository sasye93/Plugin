package loci.container

import java.nio.file.{Path, Paths}

import loci.Instance
import loci.container._
import loci.containerize.IO.Logger
import loci.containerize.types.{ContainerEntryPoint, SimplifiedConnectionEndPoint, SimplifiedContainerEntryPoint}

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
      case (m @ ModuleDef(mods, name, impl)) :: Nil =>

        /**
         * extract macro arguments.
         */
        def eq(p : String) : Boolean = (p.equalsIgnoreCase("service") || p.equalsIgnoreCase("gateway"))
        val config : String = (c.prefix.tree match {
            case q"new $s(path=$p)" if eq(s.toString) && p.toString.matches("^\".*\"$") => p
            case q"new $s($p)" if eq(s.toString) && p.toString.matches("^\".+\"$") => p
            case q"new $s($p)" if eq(s.toString) && p.toString.isEmpty => ""
            case q"new $s" => ""
            case _ => c.abort(c.enclosingPosition, "Invalid @service annotation style. Use '@service(path : String = \"\"), e.g. @service(\"scripts/mycfg.xml\") or without parameter.")
          }).toString.replaceAll("\"", "")

        val isGateway : Boolean = (c.prefix.tree match {
          case q"new $s" => s.toString.startsWith("gateway")
          case _ => false
        })
        c.info(c.enclosingPosition, "BODY: " + showRaw(impl.body),true)
        impl.body.map(_.collect{
          case a @ Apply(f, args) if(f.toString == (typeOf[loci.multitier.type].decls.find(d => d.isMethod && d.asMethod.name.toString == "start").getOrElse(NoSymbol)).fullName) =>
            //val tz : c.Tree = try{ c.typecheck(a) }
            c.info(c.enclosingPosition, "aainstac : " + showRaw(a), true);
              args.foreach(a => a collect({
                case n@Apply(_f, _args) => c.info(c.enclosingPosition, "_F; " + n, true)
                  val b = {
                    c.info(c.enclosingPosition, "1", true)
                    val containerEntryClass: String = tpe(m).symbol.fullName;
                    val containerPeerClass: String = {
                      val tp = tpeType(n)
                      if (tp != null && (tp.erasure weak_<:< c.weakTypeOf[loci.Instance[Any]].erasure))
                        tp.typeArgs.headOption.getOrElse(NoType).typeSymbol.fullName
                      else ""
                    }
                    val endPoints : List[SimplifiedConnectionEndPoint] = List[SimplifiedConnectionEndPoint]() ::: {
                      _args.filter({
                        case a@Apply(__f, __args) => (tpeType(a).resultType =:= c.typeOf[loci.language.Connections] && (!a.exists({
                          case TermName("and") => true
                          case _ => false
                        })))
                        case _ => false
                      }).flatMap{ a => //these are single connections, and they include the other peer!
                        //todo were here!!! currently only works for single connection.
                        //todo: is connect,listen,connectfirst the only methods?
                        val ap = a.asInstanceOf[Apply]
                        ap.collect({
                          case a2: Apply if (tpeType(a2).resultType =:= c.typeOf[loci.language.Connections]) => a2.collect({
                            case _a@Apply(___fun, ___args) if tpeType(_a).erasure weak_<:< c.weakTypeOf[loci.communicator.ConnectionSetup[loci.communicator.ProtocolCommon]].erasure => c.info(c.enclosingPosition, "that app" + _a.toString, true);
                              val (way, conPeer) = a2.fun match {
                                case t: TypeApply => (tpe(t.fun).toString, tpe(t.args.head).toString)
                                case _ => ("both", "unknown")
                                //todo problem is that connect can also listen with connectfirst.
                                //todo currently, cons are added multiple times bec. of schachtelung
                              }

                              new SimplifiedConnectionEndPoint(
                                conPeer,
                                (___args.collectFirst({
                                  case Literal(Constant(port: Int)) => port
                                }).getOrElse(0).toInt),
                                ___args.collectFirst({
                                  case Literal(Constant(host: String)) => host //todo
                                }).getOrElse("localhost"), //todo
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
                      }.flatten
                    }

                    /**
                     * save it.
                     *  */
                    implicit val logger : Logger = new Logger(c.universe.asInstanceOf[tools.nsc.Global].reporter)
                    val io = new IO()
                    io.serialize(new SimplifiedContainerEntryPoint(containerEntryClass, containerPeerClass, Paths.get(config).toFile, null, endPoints, isGateway), Paths.get("C:\\Users\\Simon S\\Dropbox\\Masterarbeit\\Code\\Plugin\\testoutput", containerEntryClass + ".ep"))

                    c.info(c.enclosingPosition, "simpl" + new SimplifiedContainerEntryPoint(containerEntryClass, containerPeerClass, Paths.get(config).toFile, null, endPoints).toString, true)
                    //body // :+ reify{ SimplifiedContainerEntryPoint(containerEntryClass, containerPeerClass, null, null, endPoints) } // todo config and script, refactor these classes to be applieable here, is hack now (bec direct use of constrcuctrro).
                    //c.abort(c.enclosingPosition, SimplifiedContainerEntryPoint(containerEntryClass, containerPeerClass, Paths.get(config).toFile, null, endPoints).toString)
                  }
                  c.Expr[Any](m)
                case _ => null// todo
              }))
            case _ => null // todo
        })
        c.Expr[Any](m)
      case _ => c.abort(c.enclosingPosition, "Invalid annotation: @loci.service must prepend a module object.")
    }

  }
}
