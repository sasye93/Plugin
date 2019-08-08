package containerize.options

import java.nio.file.Paths

//todo implement and test behavior
package object Options {
  sealed trait Stage{ def id : Int }

  case object file extends Stage{ def id = 1 }
  case object image extends Stage { def id = 2 }
  case object run extends Stage{ def id = 3 }

  /**
    * options and their default values.
    */
  var usejdk : Boolean = false
  var jar : Boolean = true
  var saveimages : Boolean = false
  var stage : Stage = run

  var nocache : Boolean = false

  var dockerUsername : String = ""
  var dockerPassword : String = ""
  var dockerHost : String = "localhost:8000"

  val defaultContainerPort = 0
  val defaultContainerHost = "127.0.0.1"
  val defaultContainerVersion = "1.0"

  //todo not good
  val targetDir : String = "target\\scala-" + scala.tools.nsc.Properties.versionNumberString + "\\classes"
  val unixLibraryPathPrefix : String = "/var/lib/libs/"

  val libraryBaseImageTag : String = "loci-containerize-library-base"

  val jreBaseImage : String = "openjdk:8-jre" //"openjdk:8-jre-alpine"

  def processOptions(options: List[String], error: String => Unit): Unit = {
    for(option <- options){
      option.toLowerCase match{
        case "stage:file" => Options.stage = Options.file
        case "stage:image" => Options.stage = Options.image
        case "stage:run" => Options.stage = Options.run
        case "save-images" => Options.saveimages = true
        case "nojar" => Options.jar = false
        case "usejdk" => Options.usejdk = true
        case "noinfo" => ;

        case "no-cache" => Options.nocache = true

        case "user" => Options.dockerUsername = option
        case "password" => Options.dockerPassword = option
        case "host" => Options.dockerHost = option
        case _ => error("unknown option supplied: " + option)
      }
    }
  }
}
