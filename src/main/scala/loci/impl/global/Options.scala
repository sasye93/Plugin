package loci.impl

import java.io.File
import java.nio.file.{Path, Paths}
import java.text.SimpleDateFormat
import java.util.Calendar

import loci.impl.IO.Logger
import loci.impl.Check

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

  val pluginName = "loci/impl"
  val pluginDescription = "Extends ScalaLoci to provide compiler support for direct deployment of Peers to Containers"
  val pluginHelp = "todo" //todo options descr
  val labelPrefix = "com.loci.containerize"

  case object file extends Stage{ final def id = 1 }
  case object image extends Stage{ final def id = 2 }
  case object publish extends Stage{ final def id = 3 }
  case object swarm extends Stage{ final def id = 4 }

  def published = stage >= publish

  var containerize : Boolean = false

  //todo find better path
  val tempDir : Path = Paths.get(System.getProperty("java.io.tmpdir"), "loci_containerize_temp")

  val containerHome : String = "/app"

  val dir : String = "containerize"
  val dirPrefix : String = "build_"
  val containerDir : String ="container"
  val libDir : String = "libs"
  val composeDir : String = "compose"
  val networkDir : String = "network"
  val backupDir : String = "image-backups"

  private val os : String = if (System.getProperty("os.name").toLowerCase().indexOf("win") >= 0) "windows" else "linux" //todo actually eliminate
  val errout : String = /*if(os == "windows") ">NUL 2>&1" else*/ "> /dev/null 2>&1"

  @deprecated("1.0")
  val osExt : String = "sh"

  val platform : String = "linux"
  def plExt : String = "sh"

  /**
    * options and their default values.
    */
  val swarmName : String = toolbox.getNameDenominator("Containerized_ScalaLoci_Project")

  var stage : Stage = swarm

  var showInfos : Boolean = true
  var cleanup : Boolean = true
  var cleanBuilds : Boolean = true
  var saveImages : Boolean = false

  //todo make empty, and if it is not set but needed, throw error. or alternatively, default user? + write in thesis what
  var dockerUsername : String = "scalalocicontainerize"
  var dockerPassword : String = "csEVmy7Q..jVAhF"
  var dockerHost : String = ""
  var dockerRepository : String = "thesis"

  //todo not good, maybe we really ned it as compile opt
  val targetDir : String = "target\\scala-" + scala.tools.nsc.Properties.versionNumberString + "\\classes"

  //must end with /, respectively.
  private val unixLibraryPathPrefix : String = "/var/lib/libs/"
  private val windowsLibraryPathPrefix : String = "/C:/libs/"

  def libraryPathPrefix : String = if(platform == "windows") windowsLibraryPathPrefix else unixLibraryPathPrefix

  val libraryBaseImageTag : String = "loci-loci.containerize-library-base"

  def processOptions(options: List[String], error: String => Unit): Unit = {

    options.foreach {
      case "showInfo=false" => showInfos = false
      case "cleanup=false" => cleanup = false
      case "cleanBuilds=false" => cleanBuilds = false
      case "saveImages=true" => saveImages = true

      case s if s.startsWith("dockerRepository=") => dockerRepository = s.substring("dockerRepository=".length).toLowerCase
      case s if s.startsWith("dockerUsername=") => dockerUsername = s.substring("dockerUsername=".length)
      case s if s.startsWith("dockerPassword=") => dockerPassword = s.substring("dockerPassword=".length)
      case s if s.startsWith("dockerHost=") => dockerHost = s.substring("dockerHost=".length)

      case s if s.startsWith("stage=") => s.substring("stage=".length) match {
        case "file" => stage = file
        case "image" => stage = image
        case "publish" => stage = publish
        case "swarm" => stage = swarm
      }

      case o @ _ => error("unknown option supplied: " + o)
    }
  }
  //todo warnings
  def checkConstraints(logger : Logger) : Unit = {
    if(stage >= publish){
      if(Check ! dockerUsername || Check ! dockerPassword)
        logger.warning(s"If you want to publish your images to a repository, you should provide the credentials to the registry via 'dockerUsername=XXX' and 'dockerPassword=XXX'. Otherwise the default user is used: '${ dockerUsername }'.")
      if(Check ! dockerRepository)
        logger.warning(s"You should specify the repository name to publish the images to via 'dockerRepository=XXX'. If you don't have a repository yet, you can create one at DockerHub. Otherwise your images arepushed to the default repository '${ dockerRepository }' of user '${ dockerUsername }'.")
    }
    if(saveImages)
      logger.warning("You specified the saveImages option. Note that saving image backups to your hard drive is extremely time consuming, you should deactivate it as soon as you don't need it anymore.")
  }
}
