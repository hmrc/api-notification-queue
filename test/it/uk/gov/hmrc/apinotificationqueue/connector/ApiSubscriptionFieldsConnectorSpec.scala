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

package uk.gov.hmrc.apinotificationqueue.connector



import java.util.UUID

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock
import com.github.tomakehurst.wiremock.client.WireMock._
import com.github.tomakehurst.wiremock.core.WireMockConfiguration
import org.mockito.Mockito._
import org.scalatest.BeforeAndAfterEach
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.mockito.MockitoSugar
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import uk.gov.hmrc.apinotificationqueue.config.ServiceConfiguration
import uk.gov.hmrc.http.{HeaderCarrier, NotFoundException, Upstream5xxResponse}
import uk.gov.hmrc.play.bootstrap.http.HttpClient
import uk.gov.hmrc.play.http.ws.WSHttp
import uk.gov.hmrc.play.test.UnitSpec

class ApiSubscriptionFieldsConnectorSpec extends UnitSpec with ScalaFutures with BeforeAndAfterEach with GuiceOneAppPerSuite with MockitoSugar {

  val apiSubscriptionFieldsPort = sys.env.getOrElse("WIREMOCK", "11112").toInt
  var apiSubscriptionFieldsHost = "localhost"
  val apiSubscriptionFieldsUrl = s"http://$apiSubscriptionFieldsHost:$apiSubscriptionFieldsPort"
  val wireMockServer = new WireMockServer(WireMockConfiguration.wireMockConfig().port(apiSubscriptionFieldsPort))

  class TestHttpClient extends HttpClient with WSHttp {
    override val hooks = Seq.empty
  }

  trait Setup {
    val serviceConfiguration = mock[ServiceConfiguration]
    when(serviceConfiguration.baseUrl("api-subscription-fields")).thenReturn(apiSubscriptionFieldsUrl)
    val fieldsId = UUID.randomUUID()

    implicit val hc = HeaderCarrier()
    val connector = new ApiSubscriptionFieldsConnector(new TestHttpClient(), serviceConfiguration)
  }

  override def beforeEach() {
    wireMockServer.start()
    WireMock.configureFor(apiSubscriptionFieldsHost, apiSubscriptionFieldsPort)
  }

  override def afterEach() {
    wireMockServer.stop()
  }

  "ApiSubscriptionFieldsConnector" should {

    "return details for the fields" in new Setup {
      val clientId = "abc123"
      stubFor(get(urlEqualTo(s"/field/$fieldsId"))
        .willReturn(aResponse().withStatus(200)
          .withBody( s"""{"clientId":"$clientId","apiContext":"context","fieldsId":"$fieldsId","apiVersion":"1.0"}""")))

      val result = await(connector.lookupClientId(fieldsId))

      result.clientId shouldBe clientId
    }

    "throw an http-verbs Upstream5xxResponse exception if the API Subscription Fields responds with an error" in new Setup {
      stubFor(get(urlEqualTo(s"/field/$fieldsId")).willReturn(aResponse().withStatus(500)))
      intercept[Upstream5xxResponse](await(connector.lookupClientId(fieldsId)))
    }

    "throw a NotFoundException if the fieldsId does not exist in the API Subscription Fields service" in new Setup {
      stubFor(get(urlEqualTo(s"/field/$fieldsId")).willReturn(aResponse().withStatus(404)))
      intercept[NotFoundException](await(connector.lookupClientId(fieldsId)))
    }
  }
}
