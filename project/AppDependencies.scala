import sbt._

object AppDependencies {

  val hmrcTestVersion = "3.5.0-play-25"
  val scalaTestVersion = "3.0.5"
  val scalatestplusVersion = "2.0.1"
  val mockitoVersion = "2.24.5"
  val wireMockVersion = "2.21.0"
  val customsApiCommonVersion = "1.36.0"
  val simpleReactiveMongoVersion = "7.12.0-play-25"
  val reactiveMongoTestVersion = "4.7.0-play-25"
  val testScope = "test,it"

  val simpleReactiveMongo = "uk.gov.hmrc" %% "simple-reactivemongo" % simpleReactiveMongoVersion

  val reactiveMongoTest = "uk.gov.hmrc" %% "reactivemongo-test" % reactiveMongoTestVersion % testScope

  val hmrcTest = "uk.gov.hmrc" %% "hmrctest" % hmrcTestVersion % testScope

  val scalaTest = "org.scalatest" %% "scalatest" % scalaTestVersion % testScope

  val scalaTestPlusPlay = "org.scalatestplus.play" %% "scalatestplus-play" % scalatestplusVersion % testScope

  val wireMock = "com.github.tomakehurst" % "wiremock" % wireMockVersion % testScope exclude("org.apache.httpcomponents","httpclient") exclude("org.apache.httpcomponents","httpcore")

  val mockito =  "org.mockito" % "mockito-core" % mockitoVersion % testScope

  val customsApiCommon = "uk.gov.hmrc" %% "customs-api-common" % customsApiCommonVersion withSources()

  val customsApiCommonTests = "uk.gov.hmrc" %% "customs-api-common" % customsApiCommonVersion % testScope classifier "tests"

}
