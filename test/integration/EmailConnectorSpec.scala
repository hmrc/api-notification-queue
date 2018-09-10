/*
 * Copyright 2018 HM Revenue & Customs
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

package integration

import org.scalatest.mockito.MockitoSugar
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.Application
import play.api.inject.guice.GuiceApplicationBuilder
import uk.gov.hmrc.apinotificationqueue.connector.EmailConnector
import uk.gov.hmrc.apinotificationqueue.model.{Email, SendEmailRequest}
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.test.UnitSpec
import util.externalservices.EmailService
import util.{ApiNotificationQueueExternalServicesConfig, ExternalServicesConfig, WireMockRunner}

class EmailConnectorSpec extends UnitSpec
  with GuiceOneAppPerSuite
  with MockitoSugar
  with EmailService
  with WireMockRunner {

  override implicit lazy val app: Application = GuiceApplicationBuilder().configure(Map(
      "microservice.services.email.host" -> ExternalServicesConfig.Host,
      "microservice.services.email.port" -> ExternalServicesConfig.Port,
      "microservice.services.email.context" -> ApiNotificationQueueExternalServicesConfig.EmailContext
    )).build()

  trait Setup {
    val sendEmailRequest = SendEmailRequest(List(Email("some-email@address.com")), "some-template-id",
      Map("parameters" -> "some-parameter"), force = false)

    implicit val hc = HeaderCarrier()
    lazy val connector: EmailConnector = app.injector.instanceOf[EmailConnector]
  }

  "EmailConnector" should {
    "successfully email" in new Setup {
      startMockServer()
      startEmailService()

      await(connector.send(sendEmailRequest))

      verifyEmailServiceWasCalled()
      stopMockServer()
    }
  }
}
