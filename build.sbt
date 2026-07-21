scalaVersion := "3.8.4"
val pekkoVersion = "1.6.0"

lazy val root = (project in file("."))
  .settings(
    version := "1.0.0",
    name := "cshas",
    idePackagePrefix := Some("com.baldo3000.cshas"),
    libraryDependencies ++= Seq(
      "org.scalatest" %% "scalatest" % "3.2.20" % Test,
      "org.apache.pekko" %% "pekko-actor-typed" % pekkoVersion,
      "org.apache.pekko" %% "pekko-cluster-typed" % pekkoVersion,
      "org.apache.pekko" %% "pekko-serialization-jackson" % pekkoVersion,
      "org.apache.pekko" %% "pekko-actor-testkit-typed" % pekkoVersion % Test,
      "ch.qos.logback" % "logback-classic" % "1.5.38"
    ),
    assembly / assemblyMergeStrategy := {
      case PathList("module-info.class")         => MergeStrategy.discard
      case x if x.endsWith("/module-info.class") => MergeStrategy.discard
      case x                                     =>
        val oldStrategy = (assembly / assemblyMergeStrategy).value
        oldStrategy(x)
    }
  )
