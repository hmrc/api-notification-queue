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

import org.mockito.ArgumentMatchers
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito._
import org.scalatest.mockito.MockitoSugar
import org.scalatest.{BeforeAndAfterAll, BeforeAndAfterEach}
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.Application
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.libs.json.{JsValue, Writes}
import uk.gov.hmrc.apinotificationqueue.config.ServiceConfiguration
import uk.gov.hmrc.apinotificationqueue.connector.EmailConnector
import uk.gov.hmrc.apinotificationqueue.model.{Email, SendEmailRequest}
import uk.gov.hmrc.customs.api.common.logging.CdsLogger
import uk.gov.hmrc.http.{HeaderCarrier, HttpReads, HttpResponse}
import uk.gov.hmrc.play.bootstrap.http.HttpClient
import uk.gov.hmrc.play.test.UnitSpec
import util.MockitoPassByNameHelper.PassByNameVerifier
import util.externalservices.EmailService
import util.{ApiNotificationQueueExternalServicesConfig, ExternalServicesConfig, WireMockRunner}

import scala.concurrent.{ExecutionContext, Future}

class EmailConnectorSpec extends UnitSpec
  with GuiceOneAppPerSuite
  with BeforeAndAfterEach
  with BeforeAndAfterAll
  with MockitoSugar
  with EmailService
  with WireMockRunner {

  private val mockServiceConfiguration = mock[ServiceConfiguration]
  private val mockHttpClient = mock[HttpClient]
  private val mockCdsLogger = mock[CdsLogger]

  override protected def beforeAll() {
    startMockServer()
  }

  override protected def beforeEach() {
    when(mockServiceConfiguration.baseUrl("email")).thenReturn("http://some-url")
    resetMockServer()
  }

  override protected def afterAll() {
    stopMockServer()
  }

  override implicit lazy val app: Application = GuiceApplicationBuilder().configure(Map(
      "microservice.services.email.host" -> ExternalServicesConfig.Host,
      "microservice.services.email.port" -> ExternalServicesConfig.Port,
      "microservice.services.email.context" -> ApiNotificationQueueExternalServicesConfig.EmailContext
    )).build()

  trait Setup {
    val sendEmailRequest = SendEmailRequest(List(Email("some-email@address.com")), "some-template-id",
      Map("parameters" -> "some-parameter"), force = false)

    implicit val hc = HeaderCarrier()
    val emulatedHttpVerbsException = new RuntimeException("an error")

    val connector: EmailConnector = new EmailConnector(mockHttpClient, mockServiceConfiguration, mockCdsLogger)
  }

  "EmailConnector" should {
    "successfully email" in new Setup {
      when(mockHttpClient.POST(any[String](), any[JsValue](), any[Seq[(String, String)]]())(any[Writes[JsValue]](), any[HttpReads[HttpResponse]](), any[HeaderCarrier], any[ExecutionContext]()))
        .thenReturn(Future.successful(mock[HttpResponse]))

      startEmailService()

      await(connector.send(sendEmailRequest))

      verify(mockHttpClient).POST(ArgumentMatchers.eq("http://some-url/hmrc/email"), any[JsValue](), any[Seq[(String, String)]]())(
        any(), any(), any(), any())
    }

    "log error when email service unavailable" in new Setup {
      when(mockHttpClient.POST(any(), any(), any())(any[Writes[JsValue]](), any[HttpReads[HttpResponse]](), any[HeaderCarrier], any[ExecutionContext]()))
        .thenReturn(Future.failed(emulatedHttpVerbsException))

      startEmailService()

      await(connector.send(sendEmailRequest))

      PassByNameVerifier(mockCdsLogger, "error")
        .withByNameParam("call to email service failed. url=http://some-url/hmrc/email")
        .withByNameParam(emulatedHttpVerbsException)
        .verify()

    }
  }
}
