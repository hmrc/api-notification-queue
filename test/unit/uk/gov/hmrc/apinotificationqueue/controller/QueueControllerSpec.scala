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

import java.util.UUID

import org.joda.time.DateTime
import org.mockito.Mockito._
import org.scalatest.mockito.MockitoSugar
import play.api.http.Status
import play.api.mvc.{AnyContentAsEmpty, Headers, Result}
import play.api.test.FakeRequest
import play.api.test.Helpers._
import uk.gov.hmrc.apinotificationqueue.model.Notification
import uk.gov.hmrc.apinotificationqueue.service.QueueService
import uk.gov.hmrc.http.BadRequestException
import uk.gov.hmrc.play.test.{UnitSpec, WithFakeApplication}

import scala.concurrent.Future

class QueueControllerSpec extends UnitSpec with MockitoSugar with WithFakeApplication {

  trait Setup {
    val staticUUID = "a2a432bd-8819-44b6-b270-e3ec880a0e7f"

    class StaticIDGenerator extends NotificationIdGenerator {
      override def generateId(): UUID = {
        UUID.fromString(staticUUID)
      }
    }

    val mockQueueService = mock[QueueService]
    val controllerUnderTest = new QueueController(mockQueueService, new StaticIDGenerator)
  }


  "POST /queue" should {
    "throw exception if x-client-id not present" in new Setup {
      intercept[BadRequestException] {
        controllerUnderTest.save()(FakeRequest(POST, "/queue"))
      }
    }

    "throw exception if no payload" in new Setup {
      private val request = FakeRequest(POST, "/queue", Headers("x-client-id" -> "a"), AnyContentAsEmpty)
      intercept[BadRequestException] {
        val result: Future[Result] = controllerUnderTest.save()(request)
      }
    }

    "return 201 if body and headers are present" in new Setup {
      private val request = FakeRequest(POST, "/queue", Headers("x-client-id" -> "a", "content-type" -> "application/xml"), AnyContentAsEmpty).withXmlBody(
        <xml>
          <node>Stuff</node>
        </xml>
      )

      val result = controllerUnderTest.save()(request)

      status(result) shouldBe Status.CREATED
      header("location", result) shouldBe Some(s"/notification/$staticUUID")
    }
  }

  "GET /notifications" should {
    "return 200" in new Setup {
      val result = controllerUnderTest.getAll()(FakeRequest(GET, "/notifications"))

      status(result) shouldBe Status.OK
    }
  }

  "GET /notification/:id" should {
    val uuid = java.util.UUID.randomUUID()

    "throw exception if x-client-id not present" in new Setup {
      intercept[BadRequestException] {
        controllerUnderTest.get(uuid)(FakeRequest(GET, s"/notification/$uuid"))
      }
    }

    "return 200" in new Setup {
      private val payload = "<xml>a</xml>"
      private val clientId = "abc123"
      when(mockQueueService.get(clientId, uuid)).thenReturn(Future.successful(Some(Notification(uuid, Map("content-type" -> "application/xml", "conversation-id" -> "5"), payload, DateTime.now()))))

      val result = controllerUnderTest.get(uuid)(FakeRequest(GET, s"/notification/$uuid", Headers(controllerUnderTest.CLIENT_ID_HEADER_NAME -> clientId), AnyContentAsEmpty))

      status(result) shouldBe Status.OK
      contentAsString(result) shouldBe payload
      header("conversation-id", result) shouldBe Some("5")
      header(controllerUnderTest.CLIENT_ID_HEADER_NAME, result) shouldBe None
    }

    "return 404 if not found" in new Setup {
      when(mockQueueService.get("a", uuid)).thenReturn(Future.successful(None))

      val result = controllerUnderTest.get(uuid)(FakeRequest(GET, s"/notification/$uuid", Headers("x-client-id" -> "a"), AnyContentAsEmpty))

      status(result) shouldBe Status.NOT_FOUND
    }
  }

  "DELETE /notification/:id" should {
    val uuid = java.util.UUID.randomUUID()

    "throw exception if x-client-id not present" in new Setup {
      intercept[BadRequestException] {
        controllerUnderTest.delete(uuid)(FakeRequest(DELETE, s"/notification/$uuid"))
      }
    }

    "return 204 if deleted" in new Setup {
      when(mockQueueService.delete("a", uuid)).thenReturn(Future.successful(true))

      val result = controllerUnderTest.delete(uuid)(FakeRequest(DELETE, s"/notification/$uuid", Headers("x-client-id" -> "a"), AnyContentAsEmpty))

      status(result) shouldBe Status.NO_CONTENT
    }

    "return 404 if not found" in new Setup {
      when(mockQueueService.delete("a", uuid)).thenReturn(Future.successful(false))

      val result = controllerUnderTest.delete(uuid)(FakeRequest(DELETE, s"/notification/$uuid", Headers("x-client-id" -> "a"), AnyContentAsEmpty))

      status(result) shouldBe Status.NOT_FOUND
    }
  }

}
