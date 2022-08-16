import sbt._

object AppDependencies {

  val customsApiCommonVersion = "1.57.0"
  val testScope = "test,it"
  val mongoVersion = "0.68.0"

  val compile = Seq(
    "uk.gov.hmrc" %% "customs-api-common" % customsApiCommonVersion withSources(),
    "uk.gov.hmrc.mongo" %% "hmrc-mongo-play-28" % mongoVersion,
    "com.github.ghik" % "silencer-lib" % "1.7.9" % Provided cross CrossVersion.full,
    compilerPlugin("com.github.ghik" % "silencer-plugin" % "1.7.9" cross CrossVersion.full)
  )

  val test = Seq(
    "org.scalatestplus.play" %% "scalatestplus-play" % "5.1.0" % testScope,
    "uk.gov.hmrc" %% "customs-api-common" % customsApiCommonVersion % testScope classifier "tests",
    "com.github.tomakehurst" % "wiremock-standalone" % "2.27.2" % testScope,
    "org.scalatestplus" %% "mockito-3-4" % "3.2.10.0" % testScope,
    "uk.gov.hmrc.mongo" %% "hmrc-mongo-test-play-28" % mongoVersion % testScope,
    "com.vladsch.flexmark" % "flexmark-all" % "0.35.10" % testScope
  )
}
