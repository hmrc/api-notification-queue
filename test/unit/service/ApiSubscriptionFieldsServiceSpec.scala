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

import java.util.UUID

import org.mockito.Mockito._
import org.scalatestplus.mockito.MockitoSugar
import play.api.test.Helpers
import play.api.test.Helpers.{INTERNAL_SERVER_ERROR, NOT_FOUND}
import uk.gov.hmrc.apinotificationqueue.connector.{ApiSubscriptionFieldResponse, ApiSubscriptionFieldsConnector}
import uk.gov.hmrc.apinotificationqueue.service.ApiSubscriptionFieldsService
import uk.gov.hmrc.http.{HeaderCarrier, UpstreamErrorResponse}
import util.UnitSpec

import scala.concurrent.{ExecutionContext, Future}

class ApiSubscriptionFieldsServiceSpec extends UnitSpec with MockitoSugar {

  trait Setup {
    implicit val ec: ExecutionContext = Helpers.stubControllerComponents().executionContext
    val uuid: UUID = UUID.randomUUID()
    val clientId = "abc123"
    val mockConnector: ApiSubscriptionFieldsConnector = mock[ApiSubscriptionFieldsConnector]
    val apiSubscriptionFieldsService = new ApiSubscriptionFieldsService(mockConnector)
    implicit val hc = HeaderCarrier()
  }

  "ApiSubscriptionFieldsService" should {
    "Return the client Id" in new Setup {
      when(mockConnector.lookupClientId(uuid)).thenReturn(Future.successful(ApiSubscriptionFieldResponse(clientId)))

      val result: Option[String] = await(apiSubscriptionFieldsService.getClientId(uuid))

      result shouldBe Some(clientId)
    }

    "Return none when NOT_FOUND" in new Setup {
      when(mockConnector.lookupClientId(uuid)).thenReturn(Future.failed(UpstreamErrorResponse("Not Found", NOT_FOUND)))

      val result: Option[String] = await(apiSubscriptionFieldsService.getClientId(uuid))

      result shouldBe None
    }

    "throw an UpstreamErrorResponse if error is not a NOT_FOUND error" in new Setup {
      when(mockConnector.lookupClientId(uuid)).thenReturn(Future.failed(UpstreamErrorResponse("Internal Server Error", INTERNAL_SERVER_ERROR)))

      intercept[UpstreamErrorResponse](await(apiSubscriptionFieldsService.getClientId(uuid)))
    }
  }
}
