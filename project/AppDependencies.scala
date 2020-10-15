import sbt._

object AppDependencies {

  val scalaTestPlusPlayVersion = "3.1.3"
  val mockitoVersion = "3.5.9"
  val wireMockVersion = "2.27.2"
  val customsApiCommonVersion = "1.53.0"
  val simpleReactiveMongoVersion = "7.30.0-play-27"
  val reactiveMongoTestVersion = "4.21.0-play-27"
  val testScope = "test,it"

  val simpleReactiveMongo = "uk.gov.hmrc" %% "simple-reactivemongo" % simpleReactiveMongoVersion

  val reactiveMongoTest = "uk.gov.hmrc" %% "reactivemongo-test" % reactiveMongoTestVersion % testScope

  val scalaTestPlusPlay = "org.scalatestplus.play" %% "scalatestplus-play" % scalaTestPlusPlayVersion % testScope

  val wireMock = "com.github.tomakehurst" % "wiremock-jre8" % wireMockVersion % testScope

  val mockito =  "org.mockito" % "mockito-core" % mockitoVersion % testScope

  val customsApiCommon = "uk.gov.hmrc" %% "customs-api-common" % customsApiCommonVersion withSources()

  val customsApiCommonTests = "uk.gov.hmrc" %% "customs-api-common" % customsApiCommonVersion % testScope classifier "tests"

}
