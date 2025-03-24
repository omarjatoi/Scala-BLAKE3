val scala3Version = "3.6.4"

lazy val root = project
  .in(file("."))
  .settings(
    name := "BLAKE3",
    description := "Pure Scala port of the BLAKE3 reference implementation",
    organization := "com.omarjatoi",
    version := "0.1.0",
    homepage := Some(url("https://github.com/omarjatoi/Scala-BLAKE3")),
    scalaVersion := scala3Version,
    javacOptions ++= Seq(
      "-source",
      "17",
      "-target",
      "17"
    ),
    licenses := Seq(
      "MIT" -> url("https://opensource.org/licenses/MIT"),
      "Apache-2.0" -> url("https://www.apache.org/licenses/LICENSE-2.0")
    ),
    libraryDependencies ++= Seq(
      "org.scalameta" %% "munit" % "1.0.0" % Test,
      "com.lihaoyi" %% "upickle" % "4.1.0" % Test
    )
  )
