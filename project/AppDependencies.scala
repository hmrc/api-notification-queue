import sbt._

object AppDependencies {

  val scalaTestPlusPlayVersion = "3.1.3"
  val mockitoVersion = "3.3.3"
  val wireMockVersion = "2.26.3"
  val customsApiCommonVersion = "1.50.0"
  val simpleReactiveMongoVersion = "7.26.0-play-26"
  val reactiveMongoTestVersion = "4.19.0-play-26"
  val testScope = "test,it"

  val simpleReactiveMongo = "uk.gov.hmrc" %% "simple-reactivemongo" % simpleReactiveMongoVersion

  val reactiveMongoTest = "uk.gov.hmrc" %% "reactivemongo-test" % reactiveMongoTestVersion % testScope

  val scalaTestPlusPlay = "org.scalatestplus.play" %% "scalatestplus-play" % scalaTestPlusPlayVersion % testScope

  val wireMock = "com.github.tomakehurst" % "wiremock-jre8" % wireMockVersion % testScope

  val mockito =  "org.mockito" % "mockito-core" % mockitoVersion % testScope

  val customsApiCommon = "uk.gov.hmrc" %% "customs-api-common" % customsApiCommonVersion withSources()

  val customsApiCommonTests = "uk.gov.hmrc" %% "customs-api-common" % customsApiCommonVersion % testScope classifier "tests"

}
