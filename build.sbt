lazy val root = (project in file(".")).
  settings(
    name := "BMX_PARA_OUT",
    version := "1.0",
    scalaVersion := "2.13.11"
  )

Compile / run / mainClass := Some("Main")
Compile / packageBin / mainClass := Some("Main")

assembly / mainClass := Some("Main")
assembly / assemblyJarName := "output.jar"

libraryDependencies += "ie.corballis" % "sox-java" % "1.0.3"
libraryDependencies += "com.googlecode.soundlibs" % "vorbisspi" % "1.0.3.3"
libraryDependencies += "com.googlecode.soundlibs" % "tritonus-share" % "0.3.7.4"
libraryDependencies += "com.github.scopt" %% "scopt" % "4.0.0"
libraryDependencies += "gnu.getopt" % "java-getopt" % "1.0.13"
