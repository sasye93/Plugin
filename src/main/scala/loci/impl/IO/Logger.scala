/**
  * Logger class.
  * @author Simon Schönwälder
  * @version 1.0
  */
package loci.impl.IO

import scala.reflect.internal.util.Position
import scala.sys.process.ProcessLogger
import scala.tools.nsc.reporters.Reporter

class Logger(reporter : Reporter) extends ProcessLogger {

  override def err(s: => String): Unit = error("An error occurred while trying to execute a process command: " + s)
  override def out(s: => String): Unit = {}
  override def buffer[T](f: => T): T = f

  def error(s : String, pos : Position = null): Unit = reporter.error(pos, s"Containerize: $s")
  def warning(s : String, pos : Position = null): Unit = reporter.warning(pos, s"Containerize: $s")
  def info(s : String, pos : Position = null): Unit = if(loci.impl.Options.showInfos) reporter.info(pos, s"Containerize: $s", force = true)

  object strong extends ProcessLogger{
    override def err(s: => String): Unit = error("An error occurred while trying to execute a process command: " + s)
    override def out(s: => String): Unit = info(s)
    override def buffer[T](f: => T): T = Logger.this.buffer[T](f)
  }
  object weak extends ProcessLogger{
    override def err(s: => String): Unit = warning("A warning or error occurred while trying to execute a process command: " + s)
    override def out(s: => String): Unit = {}
    override def buffer[T](f: => T): T = Logger.this.buffer[T](f)
  }

  // todo to write: class FileProcessLogger(file: File)
}
