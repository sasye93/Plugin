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

  //todo not good
  val targetDir : String = "target\\scala-" + scala.tools.nsc.Properties.versionNumberString + "\\classes"

  def processOptions(options: List[String], error: String => Unit): Unit = {
    for(option <- options){
      option.toLowerCase match{
        case "stage:file" => Options.stage = Options.file
        case "stage:image" => Options.stage = Options.image
        case "stage:run" => Options.stage = Options.run
        case "save-images" => Options.saveimages = true
        case "nojar" => Options.jar = false
        case "usejdk" => Options.usejdk = true
        case _ => error("unknown option supplied: " + option)
      }
    }
  }
}
