package loci.containerize.AST

import java.nio.file.{Path, Paths}

import loci.containerize.main.Containerize

import scala.collection.immutable.HashMap
import scala.tools.nsc.Global

class DependencyResolver[+C <: Containerize](implicit val plugin : C) {

  import plugin._
  import plugin.global._

  /**
    * get startup order
    */
  def startupOrderDependencies() : Map[ClassSymbol, List[ClassSymbol]] = {
    def getAssocEntryPoints(c : ClassSymbol) : List[ClassSymbol] = {
      EntryPointsImpls.filter(e => toolbox.weakSymbolCompare(e._2.containerPeerClass.asInstanceOf[plugin.global.Symbol], c)).toList.map(_._2.containerEntryClass.asInstanceOf[plugin.global.ClassSymbol])
    }
    EntryPointsImpls.foldLeft(HashMap[ClassSymbol, List[ClassSymbol]]())((M, e) => M + (e._1 -> e._2.containerEndPoints.filter(_.way != "listen").map(x => getAssocEntryPoints(x.connectionPeer.asClass.asInstanceOf[plugin.global.ClassSymbol])).toList.flatten))
  }

  private def filterInvalid(ep : TEntryPointMap) : TEntryPointMap = ep.filter(e => e._2.entryClassDefined && e._2.peerClassDefined)
  private def filterInvalidPeerRefs(ep : TEntryPointMap) : TEntryPointMap = ep.filter(e => PeerDefs.exists(p => toolbox.weakSymbolCompare(e._2.containerPeerClass.asInstanceOf[plugin.global.Symbol], p.classSymbol) ))
  //todo test
  private def checkPeerRefCompleteness(ep : TEntryPointMap) : TEntryPointMap = {
    PeerDefs.foreach{ p =>
      if(!ep.exists(e => toolbox.weakSymbolCompare(e._2.containerPeerClass.asInstanceOf[plugin.global.Symbol], p.classSymbol)))
        logger.error(s"No entry point found for peer class ${ p.classSymbol.fullName }, every defined Peer inside the scope of a @loci.containerize annotation must have at least one entry point starting it.")
    }
    ep
  }

  //todo include ext?... make option switch //todo excluding java home really ok? ext libs here are unique!
  def classPathDependencies() : List[Path] = {
    plugin.classPath.asClassPathString.split(";").toList.map(Paths.get(_)).filterNot(_.startsWith(System.getProperties.get("java.home").toString))
  }

  def dependencies() : Unit = {

    EntryPointsImpls = (filterInvalid _ andThen filterInvalidPeerRefs andThen checkPeerRefCompleteness)(EntryPointsImpls)
    //checkPeerRefCompleteness()


    EntryPointsImpls.toList.foreach(x => reporter.info(null, "ENTRYSS: " + x._2.containerJSONConfig, true))

    logger.info("startuporder: " + startupOrderDependencies())

    /**
    EntryPoints = PeerDefs.map{
        p => {
          val entryPoint = EntryPointsImpls.find(k => k._2. ).orNull
          if(entryPoint == null || entryPoint._containerPeer == NoSymbol)
            reporter.error(null, s"no associated peer found for entry point: ${k._1.fullNameString}, object must at least override loci.containerize.types.ContainerEntryPoint.containerPeer")
          entryPoint._containerEntryClass = k._1.asInstanceOf[entryPoint.global.Symbol]
          (k._1 -> entryPoint)
        }
      }*/

    /**
    EntryPointsClasses.foreach{ e =>
        if(!PeerClassDefs.exists(_.classSymbol.fullName == e._2._containerPeer.fullName))
          reporter.error(null, s"Couldn't find associated peer class ${ e._2._containerPeer } for entry point ${ e._1.fullName }.")
      }
      PeerClassDefs.foreach{ e =>
        if(!EntryPointsClasses.exists(_._2._containerPeer.fullName == e.classSymbol.fullName))
          reporter.error(null, s"No entry point found for peer class ${ e.classSymbol.fullName }, every defined Peer must have at least one entry point inheriting from trait loci.containerize.types.ContainerEntryPoint.")
      }
      */

    /**EntryPoints = EntryPointsClasses.map(k => k._2 -> PeerClassDefs.find(_.classSymbol.fullName == k._2._containerPeer.fullName).orNull)*/

    /*ClassDefs.filter(_.isPeer).map(p => {

      val fileDependencyList : List[TAbstractClassDef] = ClassDefs.toList
      /*
        ClassDefs.filter(x => x.isPeer && !x.equals(p)).foldLeft(ClassDefs)((list, x) =>
          list.filterNot(c => c.equals(x) || c.classSymbol.javaClassName.startsWith(x.classSymbol.javaClassName))
        ).toList
       */
      //.map(c => Paths.get(c.filePath.path).normalize)
      //todo ok? we could also do outputpath + c.symbol.javaBinaryNameString.toString
      // global.reporter.warning(null, fileList.map(_.toAbsolutePath).toString)

      p.copy(classFiles = fileDependencyList)
    })*/
  }
}
