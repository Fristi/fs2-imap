ThisBuild / scalaVersion := "3.8.4"

val fs2Version     = "3.11.0"
val ceVersion      = "3.6.3"
val munitCeVersion = "2.0.0"

lazy val root = (project in file("."))
  .settings(
    name := "fs2-imap",
    libraryDependencies ++= Seq(
      "co.fs2"           %% "fs2-core"       % fs2Version,
      "co.fs2"           %% "fs2-io"         % fs2Version,
      "org.typelevel"    %% "cats-effect"    % ceVersion,
      "jakarta.mail"      % "jakarta.mail-api" % "2.1.3",
      "org.eclipse.angus" % "angus-mail"       % "2.0.3",
      "org.typelevel"    %% "munit-cats-effect" % munitCeVersion % Test,
      "com.icegreen"      % "greenmail"        % "2.1.2"        % Test
    )
  )
