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

package uk.gov.hmrc.apinotificationqueue.controller

import java.util.UUID

import org.joda.time.DateTime
import org.mockito.ArgumentMatchers.{eq => mockEq, _}
import org.mockito.Mockito._
import org.scalatest.mockito.MockitoSugar
import play.api.http.ContentTypes.XML
import play.api.http.HeaderNames.{CONTENT_TYPE, LOCATION}
import play.api.http.Status
import play.api.mvc.{AnyContentAsEmpty, Headers}
import play.api.test.FakeRequest
import play.api.test.Helpers._
import uk.gov.hmrc.apinotificationqueue.model.Notification
import uk.gov.hmrc.apinotificationqueue.service.{ApiSubscriptionFieldsService, QueueService}
import uk.gov.hmrc.play.test.{UnitSpec, WithFakeApplication}

import scala.concurrent.Future

class QueueControllerSpec extends UnitSpec with MockitoSugar with WithFakeApplication {

  private implicit lazy val materializer = fakeApplication.materializer

  private val CLIENT_ID_HEADER_NAME = "x-client-id"
  private val SUBSCRIPTION_FIELDS_ID_HEADER_NAME = "api-subscription-fields-id"

  trait Setup {
    val clientId = "abc123"
    val uuid = UUID.randomUUID()

    val notification1 = Notification(UUID.randomUUID(), Map.empty, "<xml></xml>", DateTime.now())
    val notification2 = notification1.copy(notificationId = UUID.randomUUID())

    class StaticIDGenerator extends NotificationIdGenerator {
      override def generateId(): UUID = uuid
    }

    val mockQueueService = mock[QueueService]
    val mockFieldsService = mock[ApiSubscriptionFieldsService]
    val queueController = new QueueController(mockQueueService, mockFieldsService, new StaticIDGenerator)
  }

  "POST /queue" should {
    "return 400 when none of the `X-Client-ID` and `api-subscription-fields-id` headers are sent in the request" in new Setup {
      val result = await(queueController.save()(FakeRequest(POST, "/queue")))

      status(result) shouldBe Status.BAD_REQUEST
    }

    "return 400 when the `fieldsId` does not exist in the `api-subscription-fields` service" in new Setup {
      when(mockFieldsService.getClientId(mockEq(uuid))(any())).thenReturn(None)

      val result = await(queueController.save()(FakeRequest(POST, "/queue", Headers(SUBSCRIPTION_FIELDS_ID_HEADER_NAME -> uuid.toString), AnyContentAsEmpty)))

      status(result) shouldBe Status.BAD_REQUEST
    }

    "return 400 if the request has no payload" in new Setup {
      val request = FakeRequest(POST, "/queue", Headers(CLIENT_ID_HEADER_NAME -> clientId), AnyContentAsEmpty)

      val result = await(queueController.save()(request))

      status(result) shouldBe Status.BAD_REQUEST
    }

    "return 201, without calling `api-subscription-fields`, when the X-Client-ID header is sent to the request" in new Setup {
      private val xml = <xml>
        <node>Stuff</node>
      </xml>
      private val request = FakeRequest(POST, "/queue", Headers(CLIENT_ID_HEADER_NAME -> clientId, CONTENT_TYPE -> XML), AnyContentAsEmpty).withXmlBody(xml)
      private val notification = Notification(uuid, Map(CONTENT_TYPE -> XML), xml.toString(), DateTime.now())
      when(mockQueueService.save(mockEq(clientId), any())).thenReturn(notification)
      val result = await(queueController.save()(request))

      verify(mockFieldsService, never()).getClientId(any())(any())
      status(result) shouldBe Status.CREATED
      header(LOCATION, result) shouldBe Some(s"/notification/$uuid")
    }

    "return 201 when getting client id via subscription fields id" in new Setup {
      private val xml = <xml>
        <node>Stuff</node>
      </xml>
      private val request = FakeRequest(POST, "/queue", Headers(SUBSCRIPTION_FIELDS_ID_HEADER_NAME -> uuid.toString, CONTENT_TYPE -> XML), AnyContentAsEmpty).withXmlBody(xml)
      private val notification = Notification(uuid, Map(CONTENT_TYPE -> XML), xml.toString(), DateTime.now())
      when(mockQueueService.save(mockEq(clientId), any())).thenReturn(notification)
      when(mockFieldsService.getClientId(mockEq(uuid))(any())).thenReturn(Some(clientId))
      val result = queueController.save()(request)

      status(result) shouldBe Status.CREATED
      header(LOCATION, result) shouldBe Some(s"/notification/$uuid")
    }
  }

  "GET /notifications" should {

    "return 400 when the X-Client-ID header is not sent to the request" in new Setup {
      val result = await(queueController.getAllByClientId()(FakeRequest(GET, "/notifications")))

      status(result) shouldBe Status.BAD_REQUEST

    }

    "return 200" in new Setup {
      when(mockQueueService.get(clientId)).thenReturn(Future.successful(List(notification1, notification2)))

      val request = FakeRequest(GET, "/notifications", Headers(CLIENT_ID_HEADER_NAME -> clientId), AnyContentAsEmpty)
      val result = await(queueController.getAllByClientId()(request))

      status(result) shouldBe Status.OK

      val expectedJson = s"""{"notifications":["/notification/${notification1.notificationId}","/notification/${notification2.notificationId}"]}"""
      bodyOf(result) shouldBe expectedJson
    }

    "return empty list if there are no notifications for a specific client id" in new Setup {
      when(mockQueueService.get(clientId)).thenReturn(Future.successful(List()))

      val request = FakeRequest(GET, "/notifications", Headers(CLIENT_ID_HEADER_NAME -> clientId), AnyContentAsEmpty)
      val result = await(queueController.getAllByClientId()(request))

      status(result) shouldBe Status.OK
      bodyOf(result) shouldBe """{"notifications":[]}"""
    }
  }

  "GET /notification/:id" should {

    "return 400 when the X-Client-ID header is not sent to the request" in new Setup {
      val result = await(queueController.get(uuid)(FakeRequest(GET, s"/notification/$uuid")))

      status(result) shouldBe Status.BAD_REQUEST
    }

    "return 200" in new Setup {
      val payload = "<xml>a</xml>"
      when(mockQueueService.get(clientId, uuid)).thenReturn(Future.successful(Some(Notification(uuid, Map(CONTENT_TYPE -> XML, "conversation-id" -> "5"), payload, DateTime.now()))))

      val request = FakeRequest(GET, s"/notification/$uuid", Headers(CLIENT_ID_HEADER_NAME -> clientId), AnyContentAsEmpty)
      val result = await(queueController.get(uuid)(request))

      status(result) shouldBe Status.OK
      bodyOf(result) shouldBe payload

      header("conversation-id", result) shouldBe Some("5")
      header(CLIENT_ID_HEADER_NAME, result) shouldBe None
    }

    "return 404 if the notification is not found" in new Setup {
      when(mockQueueService.get(clientId, uuid)).thenReturn(Future.successful(None))

      val request = FakeRequest(GET, s"/notification/$uuid", Headers(CLIENT_ID_HEADER_NAME -> clientId), AnyContentAsEmpty)
      val result = await(queueController.get(uuid)(request))

      status(result) shouldBe Status.NOT_FOUND
      bodyOf(result) shouldBe "NOT FOUND"
    }
  }

  "DELETE /notification/:id" should {

    "return 400 when the X-Client-ID header is not sent in the request" in new Setup {
      val result = await(queueController.delete(uuid)(FakeRequest(DELETE, s"/notification/$uuid")))

      status(result) shouldBe Status.BAD_REQUEST
    }

    "return 204 if the notification is deleted" in new Setup {
      when(mockQueueService.delete(clientId, uuid)).thenReturn(Future.successful(true))

      val request = FakeRequest(DELETE, s"/notification/$uuid", Headers(CLIENT_ID_HEADER_NAME -> clientId), AnyContentAsEmpty)
      val result = await(queueController.delete(uuid)(request))

      status(result) shouldBe Status.NO_CONTENT
    }

    "return 404 if the notification is not found" in new Setup {
      when(mockQueueService.delete(clientId, uuid)).thenReturn(Future.successful(false))

      val request = FakeRequest(DELETE, s"/notification/$uuid", Headers(CLIENT_ID_HEADER_NAME -> clientId), AnyContentAsEmpty)
      val result = await(queueController.delete(uuid)(request))

      status(result) shouldBe Status.NOT_FOUND
      bodyOf(result) shouldBe "NOT FOUND"
    }
  }

}
