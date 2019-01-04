import io.gatling.sbt.GatlingPlugin
import sbt.Keys._
import sbt._

val moduleName = "rtp-amazon-sqs-lib"

val root = Project(id = moduleName, base = file("."))
  .enablePlugins(GatlingPlugin, GitVersioning)
  .configs(IntegrationTest)
  .settings(Defaults.itSettings: _*)
  .settings(javaOptions in Test += "-Dconfig.resource=application.test.conf")
  .settings(run := (run in Runtime).evaluated) // Required to stop Gatling plugin overriding the default "run".
  .settings(
    name := moduleName,
    organization := "uk.gov.homeoffice",
    scalaVersion := "2.11.8",
    scalacOptions ++= Seq(
      "-feature",
      "-language:implicitConversions",
      "-language:higherKinds",
      "-language:existentials",
      "-language:reflectiveCalls",
      "-language:postfixOps",
      "-Yrangepos",
      "-Yrepl-sync"
    ),
    ivyScala := ivyScala.value map {
      _.copy(overrideScalaVersion = true)
    },
    resolvers ++= Seq(
      "Artifactory Snapshot Realm" at "http://artifactory.registered-traveller.homeoffice.gov.uk/artifactory/libs-snapshot-local/",
      "Artifactory Release Realm" at "http://artifactory.registered-traveller.homeoffice.gov.uk/artifactory/libs-release-local/",
      "Typesafe Repository" at "http://repo.typesafe.com/typesafe/releases/",
      "Sonatype Snapshots" at "https://oss.sonatype.org/content/repositories/snapshots/",
      "Sonatype Releases" at "https://oss.sonatype.org/content/repositories/releases/",
      "scalaz-bintray" at "https://dl.bintray.com/scalaz/releases",
      "Kamon Repository" at "http://repo.kamon.io"
    )
  )
  .settings(libraryDependencies ++= {
    val `akka-version` = "2.4.10"
    val `play-version` = "2.5.0"
    val `elasticmq-version` = "0.10.0"
    val `gatling-version` = "2.1.7"
    val `rtp-test-lib-version` = "1.3.4"
    val `rtp-akka-lib-version` = "2.0.0"
    val `rtp-io-lib-version` = "1.7.23"

    Seq(
      "org.elasticmq" %% "elasticmq-core" % `elasticmq-version` excludeAll ExclusionRule(organization = "io.spray") withSources(),
      "org.elasticmq" %% "elasticmq-rest-sqs" % `elasticmq-version` excludeAll ExclusionRule(organization = "io.spray") withSources(),
      "com.typesafe.play" %% "play-ws" % `play-version` withSources(),
      "org.scalactic" %% "scalactic" % "2.2.6" withSources(),
      "ch.qos.logback" % "logback-classic" % "1.1.3" withSources(),
      "org.slf4j" % "jcl-over-slf4j" % "1.7.12" withSources(),
      "com.amazonaws" % "aws-java-sdk" % "1.10.62" exclude ("commons-logging", "commons-logging"),
      "de.flapdoodle.embed" % "de.flapdoodle.embed.mongo" % "1.50.2" withSources(),
      "uk.gov.homeoffice" %% "rtp-test-lib" % `rtp-test-lib-version` withSources(),
      "uk.gov.homeoffice" %% "rtp-akka-lib" % `rtp-akka-lib-version` withSources(),
      "uk.gov.homeoffice" %% "rtp-io-lib" % `rtp-io-lib-version` withSources()
    ) ++ Seq(
      "io.gatling.highcharts" % "gatling-charts-highcharts" % `gatling-version` % IntegrationTest withSources(),
      "io.gatling" % "gatling-test-framework" % `gatling-version` % IntegrationTest withSources(),
      "org.scalatest" %% "scalatest" % "2.2.4" % Test withSources(),
      "com.typesafe.akka" %% "akka-testkit" % `akka-version` % Test withSources(),
      "com.typesafe.play" %% "play-test" % `play-version` % Test withSources(),
      "com.typesafe.play" %% "play-specs2" % `play-version` % Test withSources(),
      "uk.gov.homeoffice" %% "rtp-test-lib" % `rtp-test-lib-version` % Test classifier "tests" withSources(),
      "uk.gov.homeoffice" %% "rtp-akka-lib" % `rtp-akka-lib-version` % Test classifier "tests" withSources()
    )
  })

publishTo := {
  val artifactory = sys.env.get("ARTIFACTORY_SERVER").getOrElse("http://artifactory.registered-traveller.homeoffice.gov.uk/")
  Some("release"  at artifactory + "artifactory/libs-release-local")
}

credentials += Credentials(Path.userHome / ".ivy2" / ".credentials")

publishArtifact in (Test, packageBin) := true
publishArtifact in (Test, packageDoc) := true
publishArtifact in (Test, packageSrc) := true

assemblyExcludedJars in assembly := {
  val testDependencies = (fullClasspath in Test).value
    .sortWith((f1, f2) => f1.data.getName < f2.data.getName)

  val compileDependencies = (fullClasspath in Compile).value
    .filterNot(_.data.getName.endsWith("-tests.jar"))
    .filterNot(_.data.getName.startsWith("mockito-"))
    .filterNot(_.data.getName.startsWith("specs2-"))
    .filterNot(_.data.getName.startsWith("scalatest"))
    .sortWith((f1, f2) => f1.data.getName < f2.data.getName)

  val testOnlyDependencies = testDependencies.diff(compileDependencies).sortWith((f1, f2) => f1.data.getName < f2.data.getName)
  testOnlyDependencies
}

assemblyMergeStrategy in assembly := {
  case "logback.xml" => MergeStrategy.first
  case "application.conf" => MergeStrategy.first
  case "application.test.conf" => MergeStrategy.discard
  case "version.conf" => MergeStrategy.concat
  case PathList("org", "mozilla", _*) => MergeStrategy.first
  case PathList("javax", "xml", _*) => MergeStrategy.first
  case PathList("com", "sun", _*) => MergeStrategy.first
  case PathList("org", "w3c", "dom", _*) => MergeStrategy.first
  case PathList("org", "apache", "commons", "logging", _*) => MergeStrategy.first
  case PathList("META-INF", xs @ _*) => MergeStrategy.discard
  case PathList(ps @ _*) if ps.last endsWith ".java" => MergeStrategy.discard
  case PathList(ps @ _*) if ps.last endsWith ".so" => MergeStrategy.discard
  case PathList(ps @ _*) if ps.last endsWith ".jnilib" => MergeStrategy.discard
  case PathList(ps @ _*) if ps.last endsWith ".dll" => MergeStrategy.discard
  case PathList(ps @ _*) if ps.last endsWith ".tooling" => MergeStrategy.discard
  case PathList(ps @ _*) if ps.last endsWith ".html" => MergeStrategy.discard
  case x =>
    val oldStrategy = (assemblyMergeStrategy in assembly).value
    oldStrategy(x)
}

test in assembly := {}

fork in run := true

git.useGitDescribe := true
git.gitDescribePatterns := Seq("v?.?")
git.gitTagToVersionNumber := { tag :String =>

val branchTag = if (git.gitCurrentBranch.value == "master") "" else "-" + git.gitCurrentBranch.value
val uncommit = if (git.gitUncommittedChanges.value) "-U" else ""

tag match {
  case v if v.matches("v\\d+.\\d+") => Some(s"$v.0${uncommit}".drop(1))
  case v if v.matches("v\\d+.\\d+-.*") => Some(s"${v.replaceFirst("-",".")}${branchTag}${uncommit}".drop(1))
  case _ => None
}}
