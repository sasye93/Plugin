package loci.containerize

import java.io.File
import java.nio.file.{Path, Paths}

import com.sun.javafx.PlatformUtil
import loci.containerize.IO.Logger
import loci.containerize.Check

package object Options {
  sealed trait Stage{
    def id : Int
    def >(s : Stage): Boolean = this.id > s.id
    def <(s : Stage): Boolean = s > this
    def ==(s : Stage): Boolean = !(this < s || this > s)
    def >=(s : Stage): Boolean = this > s || this == s
    def <=(s : Stage): Boolean = this < s || this == s
  }

  case object file extends Stage{ def id = 1 }
  case object image extends Stage { def id = 2 }
  case object publish extends Stage{ def id = 3 }

  var containerize : Boolean = false

  val configPathDenoter : String = loci.container.ConfigImpl.configPathDenoter
  val containerHome : String = "/app"
  val containerVolumeStore : String = s"$containerHome/data"

  val dir : String = "container"
  val dirPrefix : String = "build_"
  val libDir : String = "libs"
  val composeDir : String = "compose"
  val networkDir : String = "network"

  var os : String = if (PlatformUtil.isWindows) "windows" else "linux"
  def osExt : String = if (os == "windows") "bat" else "sh"

  /**
    * options and their default values.
    */
  var jar : Boolean = true
  var stage : Stage = publish

  var nocache : Boolean = false
  var showInfos : Boolean = true
  var cleanup : Boolean = true
  var cleanBuilds : Boolean = true //todo set faflse

  var dockerUsername : String = "sasye93"
  var dockerPassword : String = "Jana101997"
  var dockerHost : String = ""
  var dockerRepository : String = "plugin" //todo replace, maybe just with _

  val defaultContainerPort = 0
  val defaultContainerHost = "127.0.0.1"
  val defaultContainerVersion = "1.0"

  var platform : String = "linux"
  def plExt : String = if (platform == "windows") "bat" else "sh"

  //todo not good, maybe we really ned it as compile opt
  val targetDir : String = "target\\scala-" + scala.tools.nsc.Properties.versionNumberString + "\\classes"

  //must end with /, respectively.
  private val unixLibraryPathPrefix : String = "/var/lib/libs/"
  private val windowsLibraryPathPrefix : String = "/C:/libs/"

  def libraryPathPrefix : String = if(platform == "windows") windowsLibraryPathPrefix else unixLibraryPathPrefix

  val libraryBaseImageTag : String = "loci-loci.containerize-library-base"

  var jreBaseImage : String = "openjdk:8-jre" //"openjdk:8-jre-alpine"

  var saveImages : Boolean = false

  var setupScript : File = _
  private var setupScriptPath : Path = _

  def processOptions(options: List[String], error: String => Unit): Unit = {

    options.foreach {
      case "no-jar" => jar = false
      case "no-info" => showInfos = false
      case "no-cleanup" => cleanup = false
      case "no-cache" => nocache = true
      case "build-cleanup" => cleanBuilds = true

      case "platform=windows" => platform = "windows"
      case "platform=linux" => platform = "linux"

      //todo check em, print warning for large JRE; also support basic JDK.
      case "baseImage=jre" => jreBaseImage = "openjdk:8-jre"
      case "baseImage=jre-latest" => jreBaseImage = "openjdk:jre"
      case "baseImage=jre-small" => jreBaseImage = "openjdk:8-jre-small"
      case "baseImage=jre-small-latest" => jreBaseImage = "openjdk:jre-small"
      case "baseImage=jre-alpine" => jreBaseImage = "openjdk:8-jre-alpine"
      case "baseImage=jre-alpine-latest" => jreBaseImage = "openjdk:jre-alpine"

      case "save-images" => saveImages = true

      case s if s.startsWith("repo=") => dockerRepository = s.substring("repo=".length).toLowerCase
      case s if s.startsWith("user=") => dockerUsername = s.substring("user=".length)
      case s if s.startsWith("password=") => dockerPassword = s.substring("password=".length)
      case s if s.startsWith("host=") => dockerHost = s.substring("host=".length)

      case s if s.startsWith("stage=") => s.substring("stage=".length) match {
        case "file" => stage = file
        case "image" => stage = image
        case "publish" => stage = publish
      }

      case s if s.startsWith("script=") => setupScriptPath = Paths.get(s.substring("script=".length).replace("%20", " "))

      case o @ _ => error("unknown option supplied: " + o)
    }
  }
  def checkConstraints(logger : Logger) : Unit = {
    if(platform == "windows"){
      logger.info("You are using Windows containers. Make sure your Docker instance is configured to use Windows containers as well (under Windows, right click Docker tray icon -> 'Switch to Windows containers').")
      logger.warning("Using Windows containers is discouraged, as it lacks support and will blow up image sizes (multiple GB per image). Only use if absolutely necessary. If problems occur, go back to default (linux).")
    }
    if(platform == "windows" && (jreBaseImage.contains("small") || jreBaseImage.contains("alpine")))
      logger.error("Windows container do not support small or alpine jre images, omit the baseImage option or use 'baseImage:jre' or 'baseImage:jre-latest' instead.")
    if(platform == "linux" && !(jreBaseImage.contains("small") || jreBaseImage.contains("alpine")))
      logger.warning("You specified to use full size JRE on Linux containers. This will result in very large images. Use 'baseImage=jre-small' or 'baseImage=jre-alpine', if applicable for your application.")
    if(stage >= publish){
      if(Check ! dockerUsername || Check ! dockerPassword)
        logger.error("If you want to publish your images to a repository, you have to provide the credentials to the registry via 'username=XXX' and 'password=XXX'.")
      if(Check ! dockerRepository)
        logger.error("You must specify the repository name to publish the images to via 'repo=XXX'. If you don't have a repository yet, you can create one at DockerHub.")
    }
    if(Check ? setupScriptPath){
      setupScript = setupScriptPath.toFile
      logger.info(setupScript.toPath.toString)
      if(!(setupScript.exists() && setupScript.isFile))
        logger.error("You supplied a setup script via the script option, but there is no file at that path. Remember to masquerade blanks in the path with '%20'.")
    }
  }
}
