/*
 * Copyright 2018 HM Revenue & Customs
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import _root_.play.sbt.PlayScala
import play.core.PlayVersion
import play.sbt.PlayImport._
import sbt.Tests.{Group, SubProcess}
import uk.gov.hmrc.DefaultBuildSettings._
import uk.gov.hmrc.sbtdistributables.SbtDistributablesPlugin
import uk.gov.hmrc.sbtdistributables.SbtDistributablesPlugin._
import uk.gov.hmrc.versioning.SbtGitVersioning

lazy val appName = "api-notification-queue"

lazy val appDependencies: Seq[ModuleID] = compile ++ test

lazy val compile = Seq(
  ws,
  "uk.gov.hmrc" %% "bootstrap-play-25" % "1.7.0",
  "uk.gov.hmrc" %% "play-reactivemongo" % "6.2.0"
)

lazy val scope: String = "test, it"

lazy val test = Seq(
  "uk.gov.hmrc" %% "hmrctest" % "3.0.0" % scope,
  "org.scalatest" %% "scalatest" % "2.2.6" % scope,
  "org.pegdown" % "pegdown" % "1.6.0" % scope,
  "org.mockito" % "mockito-core" % "2.13.0" % scope,
  "org.scalatestplus.play" %% "scalatestplus-play" % "2.0.1" % scope,
  "com.typesafe.play" %% "play-test" % PlayVersion.current % scope,
  "com.github.tomakehurst" % "wiremock" % "2.8.0" % scope,
  "uk.gov.hmrc" %% "reactivemongo-test" % "3.1.0" % scope
)

lazy val plugins: Seq[Plugins] = Seq.empty
lazy val playSettings: Seq[Setting[_]] = Seq.empty

lazy val microservice = (project in file("."))
  .enablePlugins(Seq(PlayScala, SbtAutoBuildPlugin, SbtGitVersioning, SbtDistributablesPlugin) ++ plugins: _*)
  .settings(playSettings: _*)
  .settings(scalaSettings: _*)
  .settings(publishingSettings: _*)
  .settings(defaultSettings(): _*)
  .settings(unmanagedResourceDirectories in Compile += baseDirectory.value / "resources")
  .settings(
    name := appName,
    scalaVersion := "2.11.11",
    libraryDependencies ++= appDependencies,
    evictionWarningOptions in update := EvictionWarningOptions.default.withWarnScalaVersionEviction(false)
  )
  .settings(
    Keys.fork in Test := false,
    unmanagedSourceDirectories in Test := baseDirectory( base => List("unit", "util") map (base / "test" / _ )).value,
    addTestReportOption(Test, "test-reports")
  )
  .configs(IntegrationTest)
  .settings(inConfig(IntegrationTest)(Defaults.itSettings): _*)
  .settings(
    Keys.fork in IntegrationTest := false,
    unmanagedSourceDirectories in IntegrationTest := baseDirectory( base => List("it", "util") map (base / "test" / _ )).value,
    addTestReportOption(IntegrationTest, "int-test-reports"),
    testGrouping in IntegrationTest := oneForkedJvmPerTest((definedTests in IntegrationTest).value),
    parallelExecution in IntegrationTest := false,
    libraryDependencies ++= test
  )
  .settings(resolvers ++= Seq(
    Resolver.bintrayRepo("hmrc", "releases"),
    Resolver.jcenterRepo
  ))


def oneForkedJvmPerTest(tests: Seq[TestDefinition]) =
  tests map {
    test => Group(test.name, Seq(test), SubProcess(ForkOptions(runJVMOptions = Seq("-Dtest.name=" + test.name))))
  }

// Coverage configuration
coverageMinimum := 98
coverageFailOnMinimum := true
coverageExcludedPackages := "<empty>;com.kenshoo.play.metrics.*;.*definition.*;prod.*;testOnlyDoNotUseInAppConf.*;app.*;uk.gov.hmrc.BuildInfo"
