The following describes how to install and use the containerization extension. This is an extraction of the appendix/installation chapter and only describes how to make the extension running. For what can be done with it, see the thesis.

## Prerequisites
The containerization extension requires the following prerequisites:
* Scala 2.12.8 with sbt (v1.3.0 tested)
* ScalaLoci 0.3.0.
* Scala macro support.
* Windows 7+ (tested on Windows 7 and 10. Linux and MacOS untested and would probably require code adjustments, because code uses Windows' CMD command).
* Docker installed ("Docker for Windows/MacOS", or Docker Toolbox for Windows 7 or older), and a _running_ Docker daemon (Docker Machine).
* Bash script support (.sh) on your machine. On Windows, you can use an emulator like cygwin64.

#### Note:
* On Win10, one can also use a subsystem linux to enable bash, see e.g. https://itsfoss.com/install-bash-on-windows/ on how to do it. However, if you use a subsystem, you must make sure that the necessary commands are available from within the subsystem, especially docker installed. Therefore, it is easier to just install cygwin or similar.
* If you use cygwin, remember to add cygwin's /bin dir to the PATH env, so that the bash command is available from console.

## Important addition (this was not mentioned in the thesis document):
* Note that you need JDK (11 & 12 tested) installed. JRE is not sufficient, because the extension needs the jar executable for building the jar files.
* **Caution**: Don't use JDK 13/14, it has a known bug on Scala macros and will result in the compiler crashing (https://github.com/scala/bug/issues/11608).
* java ENV variables must be properly set (+ /bin added to PATH var).

## Repositories
The following code for the extension is available:
* Source code (this). The final usable file is **containerize.jar**.
* Samples at https://github.com/sasye93/containerization-samples
* The thesis document at https://github.com/sasye93/containerization-thesis (restricted).

## Usage and Dependencies
The extension comprises (1) macro implementations and (2) a compiler plugin stage. There are different ways to install the extension:
1. Add **containerize.jar** to the unmanaged _lib/_ directory of sbt (by default, this is <projectDir>/lib, create it if not existing). Then, add to the build.sbt:
```
autoCompilerPlugins := true
scalacOptions += s"-Xplugin:${baseDirectory.value.getAbsolutePath}\\lib\\containerize.jar"
```
 (if this causes errors because of resolving baseDirectory, write the hardcoded full path to the .jar).
_**OR**_
 
 2. Add **containerize.jar** both to the projects class path (add it as a library dependency) and as a Scala compiler plugin.
 How to do this depends on the IDE used. For instance in IntelliJ, it is done via _File -> Project Structure -> Libraries/Global Libraries -> Add} and File -> Settings -> Build,Execution,Deployment -> Compiler -> Scala Compiler -> Add under 'Compiler plugins' of the respective Module_. Note however that if you add the library and/or the plugin manually via the IDE in a sbt project, your changes might be overwritten the next time sbt synchronizes, and you cannot use the sbt commands this way, so use (1) if you can.

Additionally, ```add retrieveManaged := true;``` to the build.sbt, so that project dependencies are downloaded and can be grasped by the extension when building images.
Now, one can import loci.container._ inside the project where ever the extension shall be used. Always import the whole namespace, because there are cross dependencies. Most importantly, this module contains the macro declarations and the _Tools_ package. Also, always make sure that the Docker daemon is running when building the project.

## Build the project
There is no difference to conventional ScalaLoci projects when building (respectively compiling) a containerized project. The extension steps in when the compilation of a project is triggered. You can do this e.g. using _sbt compile_ or inside an IDE. While building, the Docker daemon must be running and reachable on the host. Note however, that one cannot directly run the result of the containerization process using _sbt run_, _scala_ or the run command inside the IDE, because this will just run the compiled code, not the containerized results. Instead, the extension will create all the files and output during build, which can then be used (refer to the thesis for the output created by the extension and how to start it).

Note that it might take very long when first building a project, because docker must download all the required images (stage "Build peer images"). Subsequent builds are usually faster because they are cached.
Uploading images to DockerHub also consumes a lot of time, especially within large projects. You can disable it to speed up things by passing the compiler option "publishImages=false" (see thesis).

### Rebuild from source code
If one wants to rebuild the extension from source code, the easiest way to do so is to create a fat jar by running _sbt assembly_ from inside the project dir. Then, re-add _project/scalac-plugin.xml_ to the jar's top level directory (only if you change the main plugin class loci/container/build/main/Containerize, you have to adjust it). The generated jar will be in target/scala-2.12/.

## Structure
_loci.container_: Contains the macro implementations.
_loci.container.impl_: Contains the build stage (compiler plugin stage) implementations.

## How to Use the Extension
The extension provides the three annotations _@containerize_, _@service_ and _@gateway_ inside loci.container. Usage is not explained here, see the thesis for everything else.
By default, images are pushed to scalalocicontainerize:thesis @ DockerHub (https://hub.docker.com/r/scalalocicontainerize/thesis).

In case of errors, contact simon.schoenwaelder@gmx.de
