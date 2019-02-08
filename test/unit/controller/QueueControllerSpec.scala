/*
 * Copyright 2019 HM Revenue & Customs
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

package unit.controller

import java.util.UUID

import org.joda.time.DateTime
import org.mockito.ArgumentMatchers.{eq => mockEq, _}
import org.mockito.Mockito._
import org.scalatest.mockito.MockitoSugar
import play.api.http.ContentTypes.XML
import play.api.http.HeaderNames.{CONTENT_TYPE, LOCATION}
import play.api.mvc.{AnyContentAsEmpty, Headers}
import play.api.test.FakeRequest
import play.api.test.Helpers._
import uk.gov.hmrc.apinotificationqueue.controller.{DateTimeProvider, NotificationIdGenerator, QueueController}
import uk.gov.hmrc.apinotificationqueue.logging.NotificationLogger
import uk.gov.hmrc.apinotificationqueue.model.{Notification, NotificationId, NotificationWithIdOnly, SeqOfHeader}
import uk.gov.hmrc.apinotificationqueue.service.{ApiSubscriptionFieldsService, QueueService}
import uk.gov.hmrc.play.test.{UnitSpec, WithFakeApplication}
import util.MockitoPassByNameHelper.PassByNameVerifier
import util.TestData._

import scala.concurrent.Future

class QueueControllerSpec extends UnitSpec with MockitoSugar with WithFakeApplication {

  private implicit lazy val materializer = fakeApplication.materializer

  private val CLIENT_ID_HEADER_NAME = "x-client-id"
  private val SUBSCRIPTION_FIELDS_ID_HEADER_NAME = "api-subscription-fields-id"
  private val CONVERSATION_ID_HEADER_NAME = "X-Conversation-ID"

  trait Setup {
    val clientId = "abc123"
    val uuid = UUID.randomUUID()

    class StaticIDGenerator extends NotificationIdGenerator {
      override def generateId(): UUID = uuid
    }

    val mockQueueService = mock[QueueService]
    val mockFieldsService = mock[ApiSubscriptionFieldsService]
    val mockLogger = mock[NotificationLogger]
    val mockDateTimeProvider = mock[DateTimeProvider]
    val queueController = new QueueController(mockQueueService, mockFieldsService, new StaticIDGenerator, mockDateTimeProvider, mockLogger)
  }

  "POST /queue" should {
    "return 400 when none of the `X-Client-ID` and `api-subscription-fields-id` headers are sent in the request" in new Setup {
      val result = await(queueController.save()(FakeRequest(POST, "/queue")))

      status(result) shouldBe BAD_REQUEST
    }

    "return 400 when the `fieldsId` does not exist in the `api-subscription-fields` service" in new Setup {
      when(mockFieldsService.getClientId(mockEq(uuid))(any())).thenReturn(None)

      val result = await(queueController.save()(FakeRequest(POST, "/queue", Headers(SUBSCRIPTION_FIELDS_ID_HEADER_NAME -> uuid.toString), AnyContentAsEmpty)))

      status(result) shouldBe BAD_REQUEST
    }

    "return 400 when the `api-subscription-fields-id` isn't a UUID" in new Setup {
      val result = await(queueController.save()(FakeRequest(POST, "/queue", Headers(SUBSCRIPTION_FIELDS_ID_HEADER_NAME -> "NOT-A_UUID"), AnyContentAsEmpty)))

      status(result) shouldBe BAD_REQUEST
      verifyLogWithHeaders(mockLogger, "error", "invalid subscriptionFieldsId NOT-A_UUID")
    }

    "return 400 if the request has no payload" in new Setup {
      val request = FakeRequest(POST, "/queue", Headers(CLIENT_ID_HEADER_NAME -> clientId), AnyContentAsEmpty)

      val result = await(queueController.save()(request))

      status(result) shouldBe BAD_REQUEST
    }

    "return 201, without calling `api-subscription-fields`, when the X-Client-ID header is sent to the request" in new Setup {
      private val xml = <xml>
        <node>Stuff</node>
      </xml>
      private val request = FakeRequest(POST, "/queue", Headers(CLIENT_ID_HEADER_NAME -> clientId, CONTENT_TYPE -> XML), AnyContentAsEmpty).withXmlBody(xml)
      private val notification = Notification(uuid, Map(CONTENT_TYPE -> XML), xml.toString(), DateTime.now(), None)
      when(mockQueueService.save(mockEq(clientId), any())).thenReturn(notification)

      val result = await(queueController.save()(request))

      verify(mockFieldsService, never()).getClientId(any())(any())
      status(result) shouldBe CREATED
      header(LOCATION, result) shouldBe Some(s"/notification/$uuid")
      verifyLogWithHeaders(mockLogger, "debug", "saving request", request.headers.headers)
    }

    "return 201 when getting client id via subscription fields id" in new Setup {
      private val xml = <xml>
        <node>Stuff</node>
      </xml>
      private val request = FakeRequest(POST, "/queue", Headers(SUBSCRIPTION_FIELDS_ID_HEADER_NAME -> uuid.toString, CONTENT_TYPE -> XML, CONVERSATION_ID_HEADER_NAME -> "test-conversation-id"), AnyContentAsEmpty).withXmlBody(xml)
      private val notification = Notification(uuid, Map(CONTENT_TYPE -> XML), xml.toString(), DateTime.now(), None)
      when(mockQueueService.save(mockEq(clientId), any())).thenReturn(notification)
      when(mockFieldsService.getClientId(mockEq(uuid))(any())).thenReturn(Some(clientId))

      val result = queueController.save()(request)

      status(result) shouldBe CREATED
      header(LOCATION, result) shouldBe Some(s"/notification/$uuid")
    }


    // TODO: exceptions should not be propagated all to way up as they will get handled by the Play2 error handler
    // TODO: we need to handle all exceptions and wrap them with out standard Customs error model
    // TODO: this is a lower priority as this is a protected service ie not public facing
    "Propagate and log exceptions thrown in subscription fields id connector" in new Setup {
      private val xml = <xml>
        <node>Stuff</node>
      </xml>
      private val request = FakeRequest(POST, "/queue", Headers(SUBSCRIPTION_FIELDS_ID_HEADER_NAME -> uuid.toString, CONTENT_TYPE -> XML, CONVERSATION_ID_HEADER_NAME -> "test-conversation-id"), AnyContentAsEmpty).withXmlBody(xml)
      private val notification = Notification(uuid, Map(CONTENT_TYPE -> XML), xml.toString(), DateTime.now(), None)
      when(mockQueueService.save(mockEq(clientId), any())).thenReturn(notification)
      when(mockFieldsService.getClientId(mockEq(uuid))(any())).thenReturn(Future.failed(emulatedServiceFailure))

      val result = await(queueController.save()(request))

      verifyLogWithHeaders(mockLogger, "error", "Error calling subscription fields id due to Emulated service failure.", request.headers.headers)
    }

  }

  "GET /notifications" should {

    "return 400 when the X-Client-ID header is not sent to the request" in new Setup {
      val result = await(queueController.getAllByClientId()(FakeRequest(GET, "/notifications")))

      status(result) shouldBe BAD_REQUEST
    }

    "return 200" in new Setup {
      val notificationWithIdOnly1 = NotificationWithIdOnly(NotificationId(UUID.randomUUID()))
      val notificationWithIdOnly2 = NotificationWithIdOnly(NotificationId(UUID.randomUUID()))
      when(mockQueueService.get(clientId, None)).thenReturn(Future.successful(List(notificationWithIdOnly1, notificationWithIdOnly2)))
      val request = FakeRequest(GET, "/notifications", Headers(CLIENT_ID_HEADER_NAME -> clientId), AnyContentAsEmpty)

      val result = await(queueController.getAllByClientId()(request))

      status(result) shouldBe OK
      val expectedJson = s"""{"notifications":["/notification/${notificationWithIdOnly1.notification.notificationId.toString}","/notification/${notificationWithIdOnly2.notification.notificationId.toString}"]}"""
      bodyOf(result) shouldBe expectedJson
    }

    "return empty list if there are no notifications for a specific client id" in new Setup {
      when(mockQueueService.get(clientId, None)).thenReturn(Future.successful(List()))
      val request = FakeRequest(GET, "/notifications", Headers(CLIENT_ID_HEADER_NAME -> clientId), AnyContentAsEmpty)

      val result = await(queueController.getAllByClientId()(request))

      status(result) shouldBe OK
      bodyOf(result) shouldBe """{"notifications":[]}"""
    }
  }

  "GET /notification/:id" should {

    "return 400 when the X-Client-ID header is not sent to the request" in new Setup {
      val result = await(queueController.get(uuid)(FakeRequest(GET, s"/notification/$uuid")))

      status(result) shouldBe BAD_REQUEST
    }

    "return 200" in new Setup {
      val payload = "<xml>a</xml>"
      when(mockQueueService.get(clientId, uuid)).thenReturn(Future.successful(Some(Notification(uuid, Map(CONTENT_TYPE -> XML, CONVERSATION_ID_HEADER_NAME -> "5"), payload, DateTime.now(), None))))
      val request = FakeRequest(GET, s"/notification/$uuid", Headers(CLIENT_ID_HEADER_NAME -> clientId), AnyContentAsEmpty)

      val result = await(queueController.get(uuid)(request))

      status(result) shouldBe OK
      bodyOf(result) shouldBe payload
      header(CONVERSATION_ID_HEADER_NAME, result) shouldBe Some("5")
      header(CLIENT_ID_HEADER_NAME, result) shouldBe None
    }

    "return 404 if the notification is not found" in new Setup {
      when(mockQueueService.get(clientId, uuid)).thenReturn(Future.successful(None))
      val request = FakeRequest(GET, s"/notification/$uuid", Headers(CLIENT_ID_HEADER_NAME -> clientId), AnyContentAsEmpty)

      val result = await(queueController.get(uuid)(request))

      status(result) shouldBe NOT_FOUND
      bodyOf(result) shouldBe "NOT FOUND"
    }
  }

  "DELETE /notification/:id" should {

    "return 400 when the X-Client-ID header is not sent in the request" in new Setup {
      val result = await(queueController.delete(uuid)(FakeRequest(DELETE, s"/notification/$uuid")))

      status(result) shouldBe BAD_REQUEST
    }

    "return 204 if the notification is deleted" in new Setup {
      when(mockQueueService.delete(clientId, uuid)).thenReturn(Future.successful(true))
      val request = FakeRequest(DELETE, s"/notification/$uuid", Headers(CLIENT_ID_HEADER_NAME -> clientId), AnyContentAsEmpty)

      val result = await(queueController.delete(uuid)(request))

      status(result) shouldBe NO_CONTENT
    }

    "return 404 if the notification is not found" in new Setup {
      when(mockQueueService.delete(clientId, uuid)).thenReturn(Future.successful(false))
      val request = FakeRequest(DELETE, s"/notification/$uuid", Headers(CLIENT_ID_HEADER_NAME -> clientId), AnyContentAsEmpty)

      val result = await(queueController.delete(uuid)(request))

      status(result) shouldBe NOT_FOUND
      bodyOf(result) shouldBe "NOT FOUND"
    }
  }

  private def verifyLogWithHeaders(logger: NotificationLogger, method: String, message: String): Unit = {
    PassByNameVerifier(logger, method)
      .withByNameParam(message)
      .withByNameParamMatcher(any[SeqOfHeader])
      .verify()
  }

  private def verifyLogWithHeaders(logger: NotificationLogger, method: String, message: String, headers: SeqOfHeader): Unit = {
    PassByNameVerifier(logger, method)
      .withByNameParam(message)
      .withByNameParam(headers)
      .verify()
  }

}
