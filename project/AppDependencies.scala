import sbt._

object AppDependencies {

  val mongoVersion      = "1.8.0"
  val bootstrapVersion  = "8.5.0"
  val playSuffix        = "-play-28"

  val compile = Seq(
    "uk.gov.hmrc"            %% s"bootstrap-backend$playSuffix" % bootstrapVersion,
    "uk.gov.hmrc.mongo"      %% s"hmrc-mongo$playSuffix"        % mongoVersion,
    "org.typelevel"          %% "cats-core"                     % "2.10.0"
  )

  val test = Seq(
    "uk.gov.hmrc"            %% s"bootstrap-test$playSuffix"  % bootstrapVersion,
    "org.wiremock"           %  "wiremock-standalone"         % "3.5.4",
    "org.scalatestplus"      %% "mockito-4-2"                 % "3.2.11.0",
    "uk.gov.hmrc.mongo"      %% s"hmrc-mongo-test$playSuffix" % mongoVersion,
    "com.vladsch.flexmark"   %  "flexmark-all"                % "0.64.8",
  ).map(_ % Test)
}
