import sbt._

object AppDependencies {

  val scalaTestPlusPlayVersion = "4.0.3"
  val mockitoVersion = "3.11.1"
  val wireMockVersion = "2.28.1"
  val customsApiCommonVersion = "1.55.0"
  val simpleReactiveMongoVersion = "8.0.0-play-26"
  val reactiveMongoTestVersion = "4.21.0-play-26"
  val testScope = "test,it"

  val simpleReactiveMongo = "uk.gov.hmrc" %% "simple-reactivemongo" % simpleReactiveMongoVersion

  val reactiveMongoTest = "uk.gov.hmrc" %% "reactivemongo-test" % reactiveMongoTestVersion % testScope

  val scalaTestPlusPlay = "org.scalatestplus.play" %% "scalatestplus-play" % scalaTestPlusPlayVersion % testScope

  val wireMock = "com.github.tomakehurst" % "wiremock-jre8" % wireMockVersion % testScope

  val mockito =  "org.mockito" % "mockito-core" % mockitoVersion % testScope

  val customsApiCommon = "uk.gov.hmrc" %% "customs-api-common" % customsApiCommonVersion withSources()

  val customsApiCommonTests = "uk.gov.hmrc" %% "customs-api-common" % customsApiCommonVersion % testScope classifier "tests"

  val silencerPlugin = compilerPlugin("com.github.ghik" % "silencer-plugin" % "1.7.5" cross CrossVersion.full)
  val silencerLib = "com.github.ghik" % "silencer-lib" % "1.7.5" % Provided cross CrossVersion.full

}
