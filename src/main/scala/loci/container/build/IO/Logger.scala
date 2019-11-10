/**
  * Logger class. Prints errors, warnings and infos.
  * @author Simon Schönwälder
  * @version 1.0
  *
  */
package loci.container.build.IO

import scala.reflect.internal.util.Position
import scala.sys.process.ProcessLogger
import scala.tools.nsc.reporters.Reporter

class Logger(reporter : Reporter) extends ProcessLogger {

  override def err(s: => String): Unit = error("An error occurred while trying to execute a process command: " + s)
  override def out(s: => String): Unit = {}
  override def buffer[T](f: => T): T = f

  def error(s : String, pos : Position = null): Unit = reporter.error(pos, s"Containerize: $s")
  def warning(s : String, pos : Position = null): Unit = reporter.warning(pos, s"Containerize: $s")
  def info(s : String, pos : Position = null): Unit = if(loci.container.build.Options.showInfos) reporter.info(pos, s"Containerize: $s", force = true)

  /**
    * print everything.
    */
  object strong extends ProcessLogger{
    override def err(s: => String): Unit = error("An error occurred while trying to execute a process command: " + s)
    override def out(s: => String): Unit = info(s)
    override def buffer[T](f: => T): T = Logger.this.buffer[T](f)
  }

  /**
    * discard infos and warnings, and don't stop on errors, print them as warnings.
    */
  object weak extends ProcessLogger{
    override def err(s: => String): Unit = warning("A warning or error occurred while trying to execute a process command: " + s)
    override def out(s: => String): Unit = {}
    override def buffer[T](f: => T): T = Logger.this.buffer[T](f)
  }
}
// todo: One could extend this to write the logging to a file using FileProcessLogger(file: File).