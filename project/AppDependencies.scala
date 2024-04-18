import sbt._

object AppDependencies {

  val testScope = "test,it"
  val mongoVersion = "1.2.0"
  val bootstrapVersion = "7.23.0"

  val compile = Seq(
    "uk.gov.hmrc"            %% "bootstrap-backend-play-28" % bootstrapVersion,
    "uk.gov.hmrc.mongo"      %% "hmrc-mongo-play-28"        % mongoVersion,
    "org.typelevel"          %% "cats-core"                 % "2.10.0"
  )
  val test = Seq(
    "uk.gov.hmrc"            %% "bootstrap-test-play-28"    % bootstrapVersion  % testScope,
    "com.github.tomakehurst" %  "wiremock-standalone"       % "2.27.2"          % testScope,
    "org.scalatestplus"      %% "mockito-4-2"               % "3.2.11.0"        % testScope,
    "uk.gov.hmrc.mongo"      %% "hmrc-mongo-test-play-28"   % mongoVersion      % testScope,
    "com.vladsch.flexmark"   %  "flexmark-all"              % "0.64.8"          % testScope,
  )
}
