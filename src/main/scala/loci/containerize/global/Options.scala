package loci.containerize

import java.io.File
import java.nio.file.{Path, Paths}
import java.text.SimpleDateFormat
import java.util.Calendar

import loci.containerize.IO.Logger
import loci.containerize.Check

package object Options {

  object toolbox{
    def getFormattedDateTimeString: String = new SimpleDateFormat("yyyyMMdd_HHmmss").format(Calendar.getInstance().getTime)
    def toUnixString(p : Path): String = p.toString.replace("\\", "/")
    def toUnixFile(s : String): String = s.replaceAll("\r\n", "\n").replaceAll("\r", "\n")
    def toInstanceClassName(obj : AnyRef) : String = obj.getClass.getName.stripSuffix("$")
    def getNameDenominator(s : String) : String = loci.container.Tools.getIpString(s)
  }

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

  case object file extends Stage{ final def id = 1 }
  case object image extends Stage{ final def id = 2 }
  case object publish extends Stage{ final def id = 3 }
  case object compose extends Stage{ final def id = 4 }

  val published = false //todo this is stub var now to get if images are published, make this different, based on this in compose the respective image is selected, print warning also bec if false, you cant use this on multiple nodes in a swarm (no access)

  var containerize : Boolean = false

  val configPathDenoter : String = loci.container.ConfigImpl.configPathDenoter
  val scriptPathDenoter : String = loci.container.ScriptImpl.scriptPathDenoter

  //todo find better path
  val tempDir : Path = Paths.get(System.getProperty("java.io.tmpdir"), "loci_containerize_temp")

  val containerHome : String = "/app"
  @deprecated("1.0")val containerVolumeStore : String = s"$containerHome/data"

  val dir : String = "containerize"
  val dirPrefix : String = "build_"
  val containerDir : String ="container"
  val libDir : String = "libs"
  val composeDir : String = "compose"
  val networkDir : String = "network"
  val backupDir : String = "image-backups"

  private val os : String = if (System.getProperty("os.name").toLowerCase().indexOf("win") >= 0) "windows" else "linux" //todo actually eliminate
  val errout : String = if(os == "windows") ">NUL 2>&1" else "> /dev/null 2>&1"

  @deprecated("1.0")
  def osExt : String = "sh"

  var platform : String = "linux"
  def plExt : String = "sh" //todo this also requires cygwin inside containers if windows

  /**
    * options and their default values.
    */
  @deprecated("1.0") private var _swarmName : String = "Containerized_ScalaLoci_Project" //todo darf auch dann keine - etc beinhalten
  @deprecated("1.0") def swarmName : String = toolbox.getNameDenominator(_swarmName)

  @deprecated("1.0") var jar : Boolean = true
  var stage : Stage = compose

  @deprecated("1.0")var nocache : Boolean = false
  @deprecated("1.0")var showInfos : Boolean = true
  @deprecated("1.0")var cleanup : Boolean = true
  @deprecated("1.0")var cleanBuilds : Boolean = true //todo set faflse
  @deprecated("1.0")var saveImages : Boolean = false //todo set faflse; throw warning takes forever

  @deprecated("1.0")var dockerUsername : String = "sasye93"
  @deprecated("1.0")var dockerPassword : String = "Jana101997"
  @deprecated("1.0")var dockerHost : String = ""
  @deprecated("1.0")var dockerRepository : String = "plugin" //todo replace, maybe just with _

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

  /**
   * todo: own macro to create a service from single image --- wait, no? global db can be provided by cloud provider, is better
   * todo: keep this global version? is referenced auf jeden fall in config below
   * recommended:
   * => jre-alpine
   * => redis (smallest) or couchdb
   */
  @deprecated("1.0")var jreBaseImage : String = "openjdk:8-jre" //"openjdk:8-jre-alpine"
  @deprecated("1.0")var dbBaseImage : Option[String] = Some("mongo:latest") //todo: everything else, starting db, persistent /data storage, etc.
  @deprecated("1.0")var customBaseImage : Option[String] = Some("httpd:latest") //todo set db and custom to none as default

  /**
    * global script & configs for every service.
    */
  @deprecated("1.0")private var setupHomePath : Path = _
  def getSetupScript(implicit logger : Logger) : Option[File] = resolvePath(setupScriptPath)
  private var setupScriptPath : Path = _
  def getSetupConfig(implicit logger : Logger) : Option[File] = resolvePath(setupConfigPath)
  private var setupConfigPath : Path = _

  @deprecated("1.0")def resolvePath(p : Path)(implicit logger : Logger) : Option[File] = if(p != null) resolvePath(p) else None
  @deprecated("1.0")def resolvePath(p : String)(implicit logger : Logger) : Option[File] = {
    try{
      Path.of(p) match{
        case null => None
        case p if (!p.isAbsolute && Check ? setupHomePath) => Some(Paths.get(setupHomePath.toString, p.toString).toFile)
        case p if (!p.isAbsolute) => logger.error(s"You can only supply a relative path to a file if you first set your home directory with 'home=XXX', otherwise you must supply an absolute path: ${p.toString}."); None
        case p @ _ => Some(p.toFile)
      }
    }
    catch{
      case _ : java.nio.file.InvalidPathException => None
      case _ : Throwable => None
    }
  }

  def processOptions(options: List[String], error: String => Unit): Unit = {

    options.foreach {
      case "no-jar" => jar = false
      case "no-info" => showInfos = false
      case "no-cleanup" => cleanup = false
      case "no-cache" => nocache = true
      case "build-cleanup" => cleanBuilds = true
      case "save-images" => saveImages = true

      case "platform=windows" => platform = "windows"
      case "platform=linux" => platform = "linux"

      case s if s.startsWith("customImage=") => customBaseImage = Some(s.substring("customImage=".length))

      //todo check em, print warning for large JRE; also support basic JDK.
      case s if s.startsWith("baseImage=") => s.substring("baseImage=".length) match{
        case "jre" => jreBaseImage = "openjdk:8-jre"
        case "jre-latest" => jreBaseImage = "openjdk:jre"
        case "jre-small" => jreBaseImage = "openjdk:8-jre-small"
        case "jre-small-latest" => jreBaseImage = "openjdk:jre-small"
        case "jre-alpine" => jreBaseImage = "openjdk:8-jre-alpine"
        case "jre-alpine-latest" => jreBaseImage = "openjdk:jre-alpine"
        case _ => //todo make this customizable, if not matching one of these direct inject
      }

      case s if s.startsWith("dbImage=") => s.substring("dbImage=".length) match{
        case "mongo" => dbBaseImage = Some("mongo:latest")
        case "couch" => dbBaseImage = Some("couchdb:latest")
        case "redis" => dbBaseImage = Some("redis:latest")
        case "mysql" => dbBaseImage = Some("mysql:latest")
        case "postgres" => dbBaseImage = Some("postgres:latest")
        case _ => //todo make this customizable, if not matching one of these direct inject
      }

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
    if(dbBaseImage.getOrElse("").contains("mysql"))
      logger.warning("Using mysql as database will produce large images. Also, using a relational database for Microservices is discouraged. Consider switching to a NoSQL database like couchDB, or if you rely on SQL, to PostgresSQL.")
    if(saveImages)
      logger.warning("You specified the save-images option. Note that saving image backups to your hard drive is extremely time consuming, you should deactivate it as soon as you don't need it anymore.")
  }
}
