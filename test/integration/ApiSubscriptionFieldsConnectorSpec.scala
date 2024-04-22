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

import java.util.UUID

import org.scalatest.concurrent.ScalaFutures
import org.scalatest.{BeforeAndAfterAll, BeforeAndAfterEach}
import org.scalatestplus.mockito.MockitoSugar
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.Application
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.test.Helpers._
import uk.gov.hmrc.apinotificationqueue.connector.{ApiSubscriptionFieldResponse, ApiSubscriptionFieldsConnector}
import uk.gov.hmrc.http.{HeaderCarrier, UpstreamErrorResponse}
import util.externalservices.ApiSubscriptionFieldsService
import util.{UnitSpec, _}

class ApiSubscriptionFieldsConnectorSpec extends UnitSpec
  with ScalaFutures
  with BeforeAndAfterEach
  with BeforeAndAfterAll
  with GuiceOneAppPerSuite
  with MockitoSugar
  with WireMockRunner
  with ApiSubscriptionFieldsService {

  override implicit lazy val app: Application =
    GuiceApplicationBuilder().configure(Map(
      "auditing.enabled" -> false,
      "microservice.services.api-subscription-fields.host" -> ExternalServicesConfig.Host,
      "microservice.services.api-subscription-fields.port" -> ExternalServicesConfig.Port,
      "microservice.services.api-subscription-fields.context" -> ApiNotificationQueueExternalServicesConfig.ApiSubscriptionFieldsContext
    )).build()


  trait Setup {
    val fieldsId: UUID = UUID.randomUUID()
    val clientId = "abc123"

    implicit val hc: HeaderCarrier = HeaderCarrier()
    val connector: ApiSubscriptionFieldsConnector = app.injector.instanceOf[ApiSubscriptionFieldsConnector]
  }

  override protected def beforeAll(): Unit = {
    startMockServer()
  }

  override protected def beforeEach(): Unit = {
    resetMockServer()
  }

  override protected def afterAll(): Unit = {
    stopMockServer()
  }

  "ApiSubscriptionFieldsConnector.lookupClientId()" should {

    "return details for the fields" in new Setup {
      startApiSubscriptionFieldsService(fieldsId = fieldsId, clientId = clientId)

      val result: ApiSubscriptionFieldResponse = await(connector.lookupClientId(fieldsId))

      result.clientId shouldBe clientId
      verifyGetSubscriptionFieldsCalled(fieldsId)
    }

    "throw an http-verbs UpstreamErrorResponse exception if the API Subscription Fields responds with an error" in new Setup {
      startApiSubscriptionFieldsService(INTERNAL_SERVER_ERROR, fieldsId, clientId)

      intercept[UpstreamErrorResponse](await(connector.lookupClientId(fieldsId)))
    }

    "throw an UpstreamErrorResponse if the fieldsId does not exist in the API Subscription Fields service" in new Setup {
      startApiSubscriptionFieldsService(NOT_FOUND, fieldsId, clientId)

      intercept[UpstreamErrorResponse](await(connector.lookupClientId(fieldsId)))
    }
  }
}
