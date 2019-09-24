package loci.containerize.AST

import java.nio.file.{Path, Paths}

import loci.containerize.main.Containerize
import loci.containerize.types.SimplifiedContainerEntryPoint

import scala.collection.immutable.HashMap
import scala.tools.nsc.Global

class DependencyResolver(implicit val plugin : Containerize) {

  import plugin._
  import global._

  def getAssocEntryPointsOfPeer(entryPoints : TEntryList, p : TPeerDef) : TEntryList = {
    entryPoints.filter(e => e.peerClassSymbolString == p.className)
  }
  def getAssocEntryPointsOfClassSymbol(entryPoints : TEntryPointMap, c : ClassSymbol) : List[ClassSymbol] = {
    entryPoints.filter(e => toolbox.weakSymbolCompare(e._2.peerClassSymbolString.asInstanceOf[plugin.global.Symbol], c)).toList.map(_._2.peerClassSymbolString.asInstanceOf[plugin.global.ClassSymbol])
  }
  /**
    * get startup order
    */
    @deprecated("1.0") // not updated
  def startupOrderDependencies(entryPoints : TEntryPointMap) : Map[ClassSymbol, List[ClassSymbol]] = {
      entryPoints.foldLeft(Map[ClassSymbol, List[ClassSymbol]]())((M, e) => M + (e._1 -> e._2.endPoints.filter(_.way != "listen").map(x => getAssocEntryPointsOfClassSymbol(entryPoints, x.connectionPeerSymbolString.asInstanceOf[plugin.global.ClassSymbol])).toList.flatten))
  }
  private def filterInvalid(entryPoints : TEntryList, peerDefs : TPeerList) : (TEntryList, TPeerList) = (entryPoints.filter(e => !(e.peerClassSymbolString.isEmpty || e.entryClassSymbolString.isEmpty)), peerDefs)
  private def filterInvalidPeerRefs(entryPoints : TEntryList, peerDefs : TPeerList) : (TEntryList, TPeerList) = (entryPoints.filter(e => peerDefs.exists(p => e.peerClassSymbolString == p.className)), peerDefs)
  //todo test
  //this is not used atm (check if every peer in module has entry point), because it is probably not desirable.
  private def checkPeerRefCompleteness(entryPoints : TEntryList, peerDefs : TPeerList) : (TEntryList, TPeerList) = {
    (
      entryPoints,
      peerDefs
    )
  }

  private def checkModuleEmptyness(entryPoints : TEntryPointMap) : TEntryPointMap = {
    //todo: check that multitier containerize is not empty regard peers + use this
    entryPoints
  }

  //todo include ext?... make option switch //todo excluding java home really ok? ext libs here are unique!
  def classPathDependencies() : List[Path] = {
    plugin.classPath.asClassPathString.split(";").toList.map(Paths.get(_)).filterNot(_.startsWith(System.getProperties.get("java.home").toString))
  }
  def classJRELibs() : List[String] = List("\"$JAVA_HOME/lib\"", "\"$JAVA_HOME/lib/ext\"")

  def dependencies(entryPoints : TEntryList, peerDefs : TPeerList) : (TEntryList, TPeerList) = {

    ((filterInvalid _).tupled andThen (filterInvalidPeerRefs _).tupled andThen (checkPeerRefCompleteness _).tupled)(entryPoints, peerDefs)

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
