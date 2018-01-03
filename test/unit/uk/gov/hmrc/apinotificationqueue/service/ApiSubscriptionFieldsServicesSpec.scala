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

package uk.gov.hmrc.apinotificationqueue.service

import java.util.UUID

import org.mockito.Mockito._
import org.scalatest.mockito.MockitoSugar
import uk.gov.hmrc.apinotificationqueue.connector.{ApiSubscriptionFieldResponse, ApiSubscriptionFieldsConnector}
import uk.gov.hmrc.http.{HeaderCarrier, NotFoundException}
import uk.gov.hmrc.play.test.UnitSpec

import scala.concurrent.Future

class ApiSubscriptionFieldsServicesSpec extends UnitSpec with MockitoSugar {

  trait Setup {
    val uuid: UUID = UUID.randomUUID()
    val clientId = "abc123"
    val mockConnector = mock[ApiSubscriptionFieldsConnector]
    val apiSubscriptionFieldsService = new ApiSubscriptionFieldsService(mockConnector)
    val hc = HeaderCarrier()
  }

  "ApiSubscriptionFieldsService" should {
    "Return the client Id" in new Setup {
      when(mockConnector.lookupClientId(uuid)(hc)).thenReturn(Future.successful(ApiSubscriptionFieldResponse(clientId)))

      val result: Option[String] = await(apiSubscriptionFieldsService.getClientId(uuid)(hc))

      result shouldBe Some(clientId)
    }

    "Return none when NOT_FOUND" in new Setup {
      when(mockConnector.lookupClientId(uuid)(hc)).thenReturn(Future.failed(new NotFoundException("Not Found")))

      val result: Option[String] = await(apiSubscriptionFieldsService.getClientId(uuid)(hc))

      result shouldBe None
    }
  }
}
