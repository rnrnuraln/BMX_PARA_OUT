lazy val root = (project in file(".")).
  settings(
    name := "BMX_PARA_OUT",
    version := "1.0",
    scalaVersion := "2.12.10"
  )

libraryDependencies += "gnu.getopt" % "java-getopt" % "1.0.13"
libraryDependencies += "com.googlecode.soundlibs" % "tritonus-share" % "0.3.7.4"
