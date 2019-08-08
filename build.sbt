name := "Plugin"

version := "0.1"

scalaVersion := "2.12.6"

resolvers += Resolver.bintrayRepo("stg-tud", "maven")

libraryDependencies ++= Seq(
  "org.scala-lang" % "scala-compiler" % scalaVersion.value,
"de.tuda.stg" %% "scala-loci-lang" % "0.2.0",
"de.tuda.stg" %% "scala-loci-serializer-upickle" % "0.2.0",
"de.tuda.stg" %% "scala-loci-communicator-tcp" % "0.2.0",
"de.tuda.stg" %% "scala-loci-lang-transmitter-basic" % "0.2.0",
"de.tuda.stg" %% "scala-loci-lang-transmitter-rescala" % "0.2.0")

addCompilerPlugin("de.tuda.stg" % "dslparadise" % "0.2.0" cross CrossVersion.patch)

addCompilerPlugin("org.scalamacros" % "paradise" % "2.1.1" cross CrossVersion.patch
)


enablePlugins(JavaAppPackaging)
enablePlugins(DockerPlugin)
