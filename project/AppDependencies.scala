import sbt._

object AppDependencies {

  val hmrcTestVersion = "3.9.0-play-26"
  val scalaTestVersion = "3.0.8"
  val scalatestplusVersion = "3.1.2"
  val mockitoVersion = "3.1.0"
  val wireMockVersion = "2.25.1"
  val customsApiCommonVersion = "1.43.0"
  val simpleReactiveMongoVersion = "7.20.0-play-26"
  val reactiveMongoTestVersion = "4.15.0-play-26"
  val testScope = "test,it"

  val simpleReactiveMongo = "uk.gov.hmrc" %% "simple-reactivemongo" % simpleReactiveMongoVersion

  val reactiveMongoTest = "uk.gov.hmrc" %% "reactivemongo-test" % reactiveMongoTestVersion % testScope

  val hmrcTest = "uk.gov.hmrc" %% "hmrctest" % hmrcTestVersion % testScope

  val scalaTest = "org.scalatest" %% "scalatest" % scalaTestVersion % testScope

  val scalaTestPlusPlay = "org.scalatestplus.play" %% "scalatestplus-play" % scalatestplusVersion % testScope

  val wireMock = "com.github.tomakehurst" % "wiremock-jre8" % wireMockVersion % testScope

  val mockito =  "org.mockito" % "mockito-core" % mockitoVersion % testScope

  val customsApiCommon = "uk.gov.hmrc" %% "customs-api-common" % customsApiCommonVersion withSources()

  val customsApiCommonTests = "uk.gov.hmrc" %% "customs-api-common" % customsApiCommonVersion % testScope classifier "tests"

}
