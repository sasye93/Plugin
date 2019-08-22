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

  val pluginName = "loci/containerize"
  val pluginDescription = "Extends ScalaLoci to provide compiler support for direct deployment of Peers to Containers"
  val pluginHelp = "todo" //todo options descr
  val labelPrefix = "com.loci.containerize"

  case object file extends Stage{ def id = 1 }
  case object image extends Stage{ def id = 2 }
  case object publish extends Stage{ def id = 3 }
  case object compose extends Stage{ def id = 4 }

  var containerize : Boolean = false

  val configPathDenoter : String = loci.container.ConfigImpl.configPathDenoter
  val scriptPathDenoter : String = loci.container.ScriptImpl.scriptPathDenoter

  val containerHome : String = "/app"
  val containerVolumeStore : String = s"$containerHome/data"

  val dir : String = "containerize"
  val dirPrefix : String = "build_"
  val containerDir : String ="container"
  val libDir : String = "libs"
  val composeDir : String = "compose"
  val networkDir : String = "network"

  var os : String = if (PlatformUtil.isWindows) "windows" else "linux"
  def osExt : String = "sh"

  var platform : String = "linux"
  def plExt : String = "sh" //todo this also requires cygwin inside containers if windows

  /**
    * options and their default values.
    */
  private var _swarmName : String = if(Check ? getClass.getPackage) getClass.getPackage.getImplementationTitle else "Containerized_ScalaLoci_Project"
  def swarmName : String = _swarmName

  var jar : Boolean = true
  var stage : Stage = compose

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


  //todo not good, maybe we really ned it as compile opt
  val targetDir : String = "target\\scala-" + scala.tools.nsc.Properties.versionNumberString + "\\classes"

  //must end with /, respectively.
  private val unixLibraryPathPrefix : String = "/var/lib/libs/"
  private val windowsLibraryPathPrefix : String = "/C:/libs/"

  def libraryPathPrefix : String = if(platform == "windows") windowsLibraryPathPrefix else unixLibraryPathPrefix

  val libraryBaseImageTag : String = "loci-loci.containerize-library-base"

  var jreBaseImage : String = "openjdk:8-jre" //"openjdk:8-jre-alpine"

  var saveImages : Boolean = false

  /**
    * global script & configs for every service.
    */
  private var setupHomePath : Path = _
  def getSetupScript(implicit logger : Logger) : Option[File] = resolvePath(setupScriptPath)
  private var setupScriptPath : Path = _
  def getSetupConfig(implicit logger : Logger) : Option[File] = resolvePath(setupConfigPath)
  private var setupConfigPath : Path = _

  def resolvePath(p : Path)(implicit logger : Logger) : Option[File] = p match{
    case null => None
    case p if (!p.isAbsolute && Check ? setupHomePath) => Some(Paths.get(setupHomePath.toString, p.toString).toFile)
    case p if (!p.isAbsolute) => logger.error(s"You can only supply a relative path to a file if you first set your home directory with 'home=XXX', otherwise you must supply an absolute path: ${p.toString}."); None
    case _ => Some(p.toFile)
  }

  object defaultConfig{
    val public : Boolean = true
    val replicas : Double = 1
    val cpu_limit : Double = 0.2
    val cpu_reserve : Double = 0.1
    val memory_limit : String = "256M"
    val memory_reserve : String = "64M"
    val deploy_mode : String = "replicated"
    val JSON : String = {
        "{" +
        "\"public\":\"" + public + "\"," +
        "\"deploy_mode\":\"" + deploy_mode + "\"," +
        "\"replicas\":\"" + replicas + "\"," +
        "\"cpu_limit\":\"" + cpu_limit + "\"," +
        "\"cpu_reserve\":\"" + cpu_reserve + "\"," +
        "\"memory_limit\":\"" + memory_limit + "\"," +
        "\"memory_reserve\":\"" + memory_reserve + "\"" +
        "}"
    }
  }


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

      case s if s.startsWith("name=") => _swarmName = s.substring("name=".length).toLowerCase

      case s if s.startsWith("repo=") => dockerRepository = s.substring("repo=".length).toLowerCase
      case s if s.startsWith("user=") => dockerUsername = s.substring("user=".length)
      case s if s.startsWith("password=") => dockerPassword = s.substring("password=".length)
      case s if s.startsWith("host=") => dockerHost = s.substring("host=".length)

      case s if s.startsWith("stage=") => s.substring("stage=".length) match {
        case "file" => stage = file
        case "image" => stage = image
        case "publish" => stage = publish
      }

      case s if s.startsWith("home=") => setupHomePath = Paths.get(s.substring("home=".length).replace("%20", " ")) //todo how to pass this again? not working
      case s if s.startsWith("script=") => setupScriptPath = Paths.get(s.substring("script=".length).replace("%20", " ")) //todo how to pass this again? not working
      case s if s.startsWith("config=") => setupConfigPath = Paths.get(s.substring("config=".length).replace("%20", " ")) //todo how to pass this again? not working

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
      val f : File = getSetupScript(logger).orNull
      if(Check ! f || !(f.exists() && f.isFile))
        logger.error("You supplied a service-wide setup script via the script option, but there is no file at that path. Remember to masquerade blanks in the path with '%20'.")
    }
    if(Check ? setupConfigPath){
      val f : File = getSetupConfig(logger).orNull
      if(Check ! f || !(f.exists() && f.isFile))
        logger.error("You supplied a service-wide default config via the config option, but there is no file at that path. Remember to masquerade blanks in the path with '%20'.")
    }
  }
}
