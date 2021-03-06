resolvers += "HMRC-open-artefacts-maven" at "https://open.artefacts.tax.service.gov.uk/maven2"
resolvers += Resolver.url("HMRC-open-artefacts-ivy", url("https://open.artefacts.tax.service.gov.uk/ivy2"))(Resolver.ivyStylePatterns)
resolvers += Resolver.jcenterRepo

addSbtPlugin("uk.gov.hmrc"        %  "sbt-auto-build"         % "3.0.0")
addSbtPlugin("com.github.gseitz"  %  "sbt-release"            % "1.0.12")
addSbtPlugin("com.typesafe.play"  %  "sbt-plugin"             % "2.7.9")
addSbtPlugin("uk.gov.hmrc"        %  "sbt-settings"           % "4.8.0")
addSbtPlugin("uk.gov.hmrc"        %  "sbt-distributables"     % "2.1.0")
addSbtPlugin("uk.gov.hmrc"        %  "sbt-git-stamp"          % "6.2.0")
addSbtPlugin("org.scoverage"      %  "sbt-scoverage"          % "1.8.1")
addSbtPlugin("org.scalastyle"     %% "scalastyle-sbt-plugin"  % "1.0.0")
