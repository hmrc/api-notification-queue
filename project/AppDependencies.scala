import sbt._

object AppDependencies {

  val customsApiCommonVersion = "1.57.0"
  val testScope = "test,it"

  val compile = Seq(
    "uk.gov.hmrc" %% "customs-api-common" % customsApiCommonVersion withSources(),
    "uk.gov.hmrc" %% "simple-reactivemongo" % "8.0.0-play-28",
    "com.github.ghik" % "silencer-lib" % "1.7.5" % Provided cross CrossVersion.full,
    compilerPlugin("com.github.ghik" % "silencer-plugin" % "1.7.5" cross CrossVersion.full)
  )

  val test = Seq(
    "org.scalatestplus.play" %% "scalatestplus-play" % "5.1.0" % testScope,
    "uk.gov.hmrc" %% "customs-api-common" % customsApiCommonVersion % testScope classifier "tests",
    "com.github.tomakehurst" % "wiremock-standalone" % "2.27.2" % testScope,
    "org.scalatestplus" %% "mockito-3-4" % "3.2.9.0" % testScope,
    "uk.gov.hmrc" %% "reactivemongo-test" % "5.0.0-play-28" % testScope,
    "com.vladsch.flexmark" % "flexmark-all" % "0.35.10" % testScope
  )
}
