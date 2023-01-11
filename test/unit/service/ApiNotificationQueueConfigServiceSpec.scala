/*
 * Copyright 2023 HM Revenue & Customs
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package unit.service

import com.typesafe.config.{Config, ConfigFactory}
import org.scalatest.matchers.should.Matchers
import org.scalatestplus.mockito.MockitoSugar
import play.api.Configuration
import uk.gov.hmrc.apinotificationqueue.service.ApiNotificationQueueConfigService
import uk.gov.hmrc.customs.api.common.config.ConfigValidatedNelAdaptor
import uk.gov.hmrc.customs.api.common.logging.CdsLogger
import uk.gov.hmrc.play.bootstrap.config.ServicesConfig
import util.UnitSpec

import scala.language.postfixOps

class ApiNotificationQueueConfigServiceSpec extends UnitSpec with MockitoSugar with Matchers {

  private val validAppConfig: Config = ConfigFactory.parseString(
    s"""
      |{
      |notification.email.queueThreshold=2
      |notification.email.enabled=true
      |notification.email.address="some.address@domain.com"
      |notification.email.interval=1440
      |notification.email.delay=1
      |ttlInSeconds=600
      |
      |  microservice {
      |    services {
      |      api-subscription-fields {
      |        host = localhost
      |        port = 9648
      |        context = "/field"
      |      }
      |      email {
      |        host = localhost
      |        port = 8300
      |        context = /hmrc/email
      |      }
      |    }
      |  }
      |}
    """.stripMargin)

  private val emptyAppConfig: Config = ConfigFactory.parseString("")

  private def testServicesConfig(configuration: Configuration) = new ServicesConfig(configuration) {}

  private val validServicesConfig = new Configuration(validAppConfig)
  private val emptyServicesConfig = new Configuration(emptyAppConfig)

  private val mockCdsLogger = mock[CdsLogger]

  "ConfigService" should {
    "return config as object model when configuration is valid" in {
      val actual: ApiNotificationQueueConfigService = configService(validServicesConfig)

      actual.emailConfig.emailServiceUrl shouldBe "http://localhost:8300/hmrc/email"
      actual.emailConfig.notificationEmailEnabled shouldBe true
      actual.emailConfig.notificationEmailAddress shouldBe "some.address@domain.com"
      actual.emailConfig.notificationEmailDelay shouldBe 1
      actual.emailConfig.notificationEmailInterval shouldBe 1440
      actual.emailConfig.notificationEmailQueueThreshold shouldBe 2
      actual.apiSubscriptionFieldsServiceUrl shouldBe "http://localhost:9648/field"
    }

    "throw an exception when configuration is invalid, that contains AGGREGATED error messages" in {
      val expected = """
                       |Could not find config key 'email.host'
                       |Service configuration not found for key: email.context
                       |Could not find config key 'notification.email.enabled'
                       |Could not find config key 'notification.email.queueThreshold'
                       |Could not find config key 'notification.email.address'
                       |Could not find config key 'notification.email.interval'
                       |Could not find config key 'notification.email.delay'
                       |Could not find config key 'api-subscription-fields.host'
                       |Service configuration not found for key: api-subscription-fields.context
                       |Could not find config key 'ttlInSeconds'""".stripMargin

      val caught = intercept[IllegalStateException]{ configService(emptyServicesConfig) }

      caught.getMessage shouldBe expected
    }
  }

  private def configService(conf: Configuration) =
    new ApiNotificationQueueConfigService(new ConfigValidatedNelAdaptor(testServicesConfig(conf), conf), mockCdsLogger)

}
