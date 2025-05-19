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

package integration

import com.github.tomakehurst.wiremock.client.WireMock.{aResponse, post, postRequestedFor, serverError, urlEqualTo}
import org.mockito.Mockito._
import org.scalatest.{BeforeAndAfterAll, BeforeAndAfterEach}
import org.scalatestplus.mockito.MockitoSugar
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.Application
import play.api.http.Status.ACCEPTED
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.libs.json.Json
import play.api.test.Helpers
import uk.gov.hmrc.apinotificationqueue.connector.EmailConnector
import uk.gov.hmrc.apinotificationqueue.model.{Email, EmailConfig, SendEmailRequest}
import uk.gov.hmrc.apinotificationqueue.service.ApiNotificationQueueConfigService
import uk.gov.hmrc.customs.api.common.logging.CdsLogger
import uk.gov.hmrc.http.test.{HttpClientV2Support, WireMockSupport}
import uk.gov.hmrc.http.HeaderCarrier
import util.MockitoPassByNameHelper.PassByNameVerifier
import util.{ApiNotificationQueueExternalServicesConfig, UnitSpec}

import scala.concurrent.ExecutionContext

class EmailConnectorSpec extends UnitSpec
  with GuiceOneAppPerSuite
  with BeforeAndAfterEach
  with BeforeAndAfterAll
  with MockitoSugar
  with WireMockSupport
  with HttpClientV2Support {


  implicit val ec: ExecutionContext = Helpers.stubControllerComponents().executionContext
  private val mockConfig = mock[ApiNotificationQueueConfigService]
  private val mockCdsLogger = mock[CdsLogger]
  private val mockEmailConfig = mock[EmailConfig]


  override protected def beforeEach(): Unit = {
    when(mockConfig.emailConfig).thenReturn(mockEmailConfig)
    when(mockEmailConfig.emailServiceUrl).thenReturn("http://localhost:6001/hmrc/email")
    wireMockServer.resetAll()
  }


  override implicit lazy val app: Application = GuiceApplicationBuilder().configure(Map(
      "microservice.services.email.host" -> wireMockHost,
      "microservice.services.email.port" -> wireMockPort,
      "microservice.services.email.context" -> ApiNotificationQueueExternalServicesConfig.EmailContext
    )).build()

  trait Setup {
    val sendEmailRequest: SendEmailRequest = SendEmailRequest(List(Email("some-email@address.com")), "some-template-id",
      Map("parameters" -> "some-parameter"), force = false)

    implicit val hc: HeaderCarrier = HeaderCarrier()

    val connector: EmailConnector = new EmailConnector(httpClientV2, mockConfig, mockCdsLogger)
  }

  "EmailConnector" should {
    "successfully email" in new Setup {
      wireMockServer.stubFor(post(urlEqualTo(ApiNotificationQueueExternalServicesConfig.EmailContext)).willReturn(aResponse().withStatus(ACCEPTED)))

      await(connector.send(sendEmailRequest))

      wireMockServer.verify(1, postRequestedFor(urlEqualTo(ApiNotificationQueueExternalServicesConfig.EmailContext)))

      PassByNameVerifier(mockCdsLogger, "info")
        .withByNameParam(s"""sending notification warnings email:: ${Json.toJson(sendEmailRequest)}""")
        .verify()

      PassByNameVerifier(mockCdsLogger, "debug")
        .withByNameParam("response status from email service was 202")
        .verify()
    }

    "log error when email service unavailable" in new Setup {

      wireMockServer
        .stubFor(
          post(urlEqualTo(ApiNotificationQueueExternalServicesConfig.EmailContext))
            .willReturn(serverError().withBody("an error")))

      await(connector.send(sendEmailRequest))

      PassByNameVerifier(mockCdsLogger, "error")
        .withByNameParam("call to email service failed. url=http://localhost:6001/hmrc/email, with response=an error")
        .verify()

    }
  }
}
