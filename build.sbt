name := "git_zk_monitor"

organization := "com.randomstatistic"

version := "0.1"

scalaVersion := "2.10.3"

libraryDependencies ++= Seq(
    "org.apache.zookeeper" % "zookeeper" % "3.4.5" excludeAll (
      ExclusionRule(organization = "log4j"),
      ExclusionRule(organization = "org.slf4j")
      ),
    "org.slf4j"            %  "log4j-over-slf4j"   % "1.7.5",
    "org.slf4j"            %  "slf4j-simple"       % "1.7.5" % "test",
    "org.eclipse.jgit" % "org.eclipse.jgit" % "3.3.2.201404171909-r",
    "com.github.scopt" %% "scopt" % "3.2.0",
    "org.apache.curator" % "curator-framework" % "2.4.1"
)
