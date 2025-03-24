val scala3Version = "3.6.4"

lazy val root = project
  .in(file("."))
  .settings(
    name := "BLAKE3",
    version := "0.1.0-SNAPSHOT",
    scalaVersion := scala3Version,
    libraryDependencies ++= Seq(
      "org.scalameta" %% "munit" % "1.0.0" % Test,
      "com.lihaoyi" %% "upickle" % "4.1.0" % Test
    )
  )
