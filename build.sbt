name := "Plugin"

version := "0.1"

scalaVersion := "2.12.6"

resolvers += Resolver.bintrayRepo("stg-tud", "maven")

libraryDependencies ++= Seq(
  "org.scala-lang" % "scala-compiler" % scalaVersion.value,
  "de.tuda.stg" %% "scala-loci-lang" % "0.2.0"
)


enablePlugins(JavaAppPackaging)
enablePlugins(DockerPlugin)
