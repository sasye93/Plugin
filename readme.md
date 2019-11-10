The following describes how to install and use the containerization extension. _This is an extraction of chapter appendix/installation in the thesis, go there for more info._

## Prerequisites
The containerization extension requires the following prerequisites:
* Scala 2.12.8 with sbt.
* ScalaLoci 0.3.0.
* Scala macro support.
* Windows 7+ (Linux and MacOS untested).
* Docker installed (Docker for Windows/Mac, or Docker Toolbox for Windows 7 or older), and a \underline{running} Docker daemon (Docker Machine).
* Bash script support (.sh) on your machine. The bash shell is built-in on Linux, MacOS and Windows 10. On Windows 7 or older, you can use an emulator like cygwin64.

## Repositories
The following code for the extension is available:
* Source code (this). The final usable file is **containerize.jar**.
* Samples at https://github.com/sasye93/containerization-samples
* The thesis document at https://github.com/sasye93/containerization-thesis (restricted).

## Usage and Dependencies
There are different ways to install the extension:
1. Add **containerize.jar** to the unmanaged _lib/_ directory of sbt (by default, this is <projectDir>/lib, create it if not existing).
Then, add to the build.sbt:
```
autoCompilerPlugins := true
scalacOptions += s"-Xplugin:$${baseDirectory.value.getAbsolutePath}\\lib\\containerize.jar"
```
_**OR**_
 
 2. Add **containerize.jar** both to the projects class path (add it as a library dependency) and as a Scala compiler plugin.
 How to do this depends on the IDE used. For instance in IntelliJ, it is done via _File -> Project Structure -> Libraries/Global Libraries -> Add} and File -> Settings -> Build,Execution,Deployment -> Compiler -> Scala Compiler -> Add under 'Compiler plugins' of the respective Module_. Note however that if you add the library and/or the plugin manually via the IDE in a sbt project, your changes might be overwritten the next time sbt synchronizes.

Additionally, ```add retrieveManaged := true;``` to the build.sbt, so that project dependencies are downloaded and can be grasped by the extension when building images.
Now, one can import loci.container._ inside the project where ever the extension shall be used. Always import the whole namespace, because there are cross dependencies. Most importantly, this module contains the macro declarations and the _Tools_ package. Also, always make sure that the Docker daemon is running when building the project.

## Build the project
There is no difference to conventional ScalaLoci projects when building (respectively compiling) a containerized project. The extension steps in when the compilation of a project is triggered. You can do this e.g. using _sbt compile_ or inside an IDE. While building, the Docker daemon must be running and reachable on the host. Note however, that one cannot directly run the result of the containerization process using _sbt run_, _scala_ or the run command inside the IDE, because this will just run the compiled code, not the containerized results. Instead, the extension will create all the files and output during build, which can then be used.
If one wants to rebuild the extension from source code, the easiest way to do so is to create a fat jar using _sbt assembly_ and re-add _scalac-plugin.xml_ to its top level directory.

