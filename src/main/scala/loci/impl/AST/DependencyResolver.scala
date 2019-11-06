/**
  * Checks validity of dependencies and macro usages.
  * @author Simon Schönwälder
  * @version 1.0
  */
package loci.impl.AST

import java.nio.file.{Path, Paths}
import loci.impl.main.Containerize

class DependencyResolver(implicit val plugin : Containerize) {

  import plugin._

  /**
    * Get the associated @containerize object of a @service/@gateway
    * (the @containerize object in which the peer that is started inside the @service/@gateway has been declared).
    * @param entryPoint The entry point (@service/@gateway).
    * @param Modules List of pickled modules (@containerize).
    * @return The @containerize module.
    */
  def getModuleOfEntryPoint(entryPoint: TSimpleEntryDef, Modules : TModuleList) : Option[TModuleDef] = {
    Modules.find(_.peers.exists(entryPoint.peerClassSymbolString == _.className))
  }
  /**
    * Get all @service/@gateway objects that start peer.
    * @param entryPoints List of all @service/@gateway objects.
    * @param peer a @peer def.
    * @return List of the objects.
    */
  def getAssocEntryPointsOfPeer(entryPoints : TEntryList, peer : TPeerDef) : TEntryList = {
    entryPoints.filter(e => e.peerClassSymbolString == peer.className)
  }

  /**
    * The following functions perform some filtering on the lists of @containerize/@service/@gateway objects,
    * like filtering out disabled @containerize objects, invalid defs, and so on.
    */
  def filterDisabled(modules : TModuleList) : TModuleList = modules.filterNot(_.config.getDisabled)
  private def filterInvalid(entryPoints : TEntryList, peerDefs : TPeerList) : (TEntryList, TPeerList) = (entryPoints.filter(e => !(e.peerClassSymbolString.isEmpty || e.entryClassSymbolString.isEmpty)), peerDefs)
  private def filterInvalidPeerRefs(entryPoints : TEntryList, peerDefs : TPeerList) : (TEntryList, TPeerList) = (entryPoints.filter(e => peerDefs.exists(p => e.peerClassSymbolString == p.className)), peerDefs)
  //this does nothing atm (check if every peer in module has entry point), because it is probably not desirable.
  private def checkPeerRefCompleteness(entryPoints : TEntryList, peerDefs : TPeerList) : (TEntryList, TPeerList) = (entryPoints, peerDefs)

  /**
    * The classXX methods extract the list of dependencies of the project from the class path.
    * @return
    */
  def classPathDependencies() : List[Path] = {
    plugin.classPath.asClassPathString.split(";").toList.map(Paths.get(_)).filterNot(_.startsWith(System.getProperties.get("java.home").toString))
  }
  def classJRELibs() : List[String] = List("\"$JAVA_HOME/lib\"", "\"$JAVA_HOME/lib/ext\"")

  /**
    * One-in-all function to perform all filtering.
    */
  def dependencies(entryPoints : TEntryList, peerDefs : TPeerList) : (TEntryList, TPeerList) = {
    ((filterInvalid _).tupled andThen (filterInvalidPeerRefs _).tupled andThen (checkPeerRefCompleteness _).tupled)(entryPoints, peerDefs)
  }
}
