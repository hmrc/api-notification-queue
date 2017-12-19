/*
 * Copyright 2017 HM Revenue & Customs
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

package uk.gov.hmrc.apinotificationqueue.controller

import org.scalatest.mockito.MockitoSugar
import play.api.http.Status
import play.api.mvc.{AnyContentAsEmpty, Headers, Result}
import play.api.test.FakeRequest
import uk.gov.hmrc.apinotificationqueue.service.QueueService
import uk.gov.hmrc.http.BadRequestException
import uk.gov.hmrc.play.test.{UnitSpec, WithFakeApplication}

import scala.concurrent.Future

class QueueControllerSpec extends UnitSpec with MockitoSugar with WithFakeApplication {

  trait Setup {
    val mockQueueService = mock[QueueService]
    val controllerUnderTest = new QueueController(mockQueueService)
  }


  "POST /queue" should {
    "throw exception if x-client-id not present" in new Setup {
      intercept[BadRequestException] {
        controllerUnderTest.save()(FakeRequest("POST", "/queue"))
      }
    }
    "throw exception if no payload" in new Setup {
      private val request = FakeRequest("POST", "/queue", Headers("x-client-id" -> "a"), AnyContentAsEmpty)
      intercept[BadRequestException] {
        val result: Future[Result] = controllerUnderTest.save()(request)
      }
    }
    "return 201 if body and headers are present" in new Setup {
      private val request = FakeRequest("POST", "/queue", Headers("x-client-id" -> "a", "content-type" -> "application/xml"), AnyContentAsEmpty).withXmlBody(
        <xml>
          <node>Stuff</node>
        </xml>
      )
      val result = controllerUnderTest.save()(request)
      status(result) shouldBe Status.CREATED
    }
  }

  "GET /messages" should {
    "return 200" in new Setup {
      val result = controllerUnderTest.getAll()(FakeRequest("GET", "/messages"))
      status(result) shouldBe Status.OK
    }
  }

  "GET /message/:id" should {
    "return 200" in new Setup {
      val uuid = java.util.UUID.randomUUID()
      val result = controllerUnderTest.get(uuid)(FakeRequest("GET", s"/message/$uuid"))
      status(result) shouldBe Status.OK
    }
  }

}
