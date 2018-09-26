publishTo := {
  val artifactory = sys.env.get("ARTIFACTORY_SERVER").getOrElse("http://artifactory.registered-traveller.homeoffice.gov.uk/")

  if (isSnapshot.value)
    Some("snapshot" at artifactory + "artifactory/libs-snapshot-local")
  else
    Some("release"  at artifactory + "artifactory/libs-release-local")
}

credentials += Credentials(Path.userHome / ".ivy2" / ".credentials")

// Enable publishing the jar produced by `test:package`
publishArtifact in (Test, packageBin) := true

// Enable publishing the test API jar
publishArtifact in (Test, packageDoc) := true

// Enable publishing the test sources jar
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
