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

package unit.controller

import java.util.UUID

import org.joda.time.DateTime
import org.mockito.ArgumentMatchers.{eq => mockEq, _}
import org.mockito.Mockito._
import org.scalatestplus.mockito.MockitoSugar
import play.api.http.ContentTypes.XML
import play.api.http.HeaderNames.{CONTENT_TYPE, LOCATION}
import play.api.mvc.{AnyContentAsEmpty, Headers}
import play.api.test.Helpers._
import play.api.test.{FakeRequest, Helpers}
import uk.gov.hmrc.apinotificationqueue.controller.{DateTimeProvider, QueueController}
import uk.gov.hmrc.apinotificationqueue.logging.NotificationLogger
import uk.gov.hmrc.apinotificationqueue.model.{Notification, NotificationId, NotificationWithIdOnly, SeqOfHeader}
import uk.gov.hmrc.apinotificationqueue.service.{ApiSubscriptionFieldsService, QueueService, UuidService}
import uk.gov.hmrc.http.HeaderCarrier
import util.MockitoPassByNameHelper.PassByNameVerifier
import util.TestData._
import util.{MaterializerSupport, UnitSpec}

import scala.concurrent.Future

class QueueControllerSpec extends UnitSpec with MockitoSugar with MaterializerSupport {

  private implicit val ec = Helpers.stubControllerComponents().executionContext

  private val CLIENT_ID_HEADER_NAME = "x-client-id"
  private val SUBSCRIPTION_FIELDS_ID_HEADER_NAME = "api-subscription-fields-id"
  private val CONVERSATION_ID_HEADER_NAME = "X-Conversation-ID"
  private val NOTIFICATION_ID_HEADER_NAME = "notification-id"

  trait Setup {
    val clientId = "b540741d-4d55-4fc8-9f1e-22dbc853bb12"
    val fieldsId = "1f95578f-2eba-4ce7-8afa-08dc71d580eb"
    val notificationIdHeaderValue = "53d1c27f-7af9-4310-8313-2e4f24766995"
    val notificationId = UUID.fromString("e4cdd797-fcc6-4b32-b4e1-8a8459c48cb6")

    val mockQueueService = mock[QueueService]
    val mockFieldsService = mock[ApiSubscriptionFieldsService]
    val mockLogger = mock[NotificationLogger]
    val mockDateTimeProvider = mock[DateTimeProvider]
    val mockUuidService = mock[UuidService]
    val controllerComponents = Helpers.stubControllerComponents()
    val queueController = new QueueController(mockQueueService, mockFieldsService, mockUuidService, mockDateTimeProvider, controllerComponents, mockLogger)
  }

  "POST /queue" should {
    "return 400 when `api-subscription-fields-id` header is missing" in new Setup {
      val result = await(queueController.save()(FakeRequest(POST, "/queue")))

      status(result) shouldBe BAD_REQUEST
      verifyLogWithHeaders(mockLogger, "error", "missing api-subscription-fields-id header when calling save endpoint", List((HOST, "localhost")))
    }

    "return 400 when the `fieldsId` does not exist in the `api-subscription-fields` service" in new Setup {
      when(mockFieldsService.getClientId(mockEq(UUID.fromString(fieldsId)))(any())).thenReturn(None)

      val request = FakeRequest(POST, "/queue", Headers(SUBSCRIPTION_FIELDS_ID_HEADER_NAME -> fieldsId), AnyContentAsEmpty)
      val result = await(queueController.save()(request))

      status(result) shouldBe BAD_REQUEST
      verifyLogWithHeaders(mockLogger, "error", s"unable to retrieve clientId from api-subscription-fields service for fieldsId $fieldsId", request.headers.headers)
    }

    "return 400 when the `api-subscription-fields-id` isn't a UUID" in new Setup {
      val request = FakeRequest(POST, "/queue", Headers(SUBSCRIPTION_FIELDS_ID_HEADER_NAME -> "NOT-A_UUID"), AnyContentAsEmpty)

      val result = await(queueController.save()(request))

      status(result) shouldBe BAD_REQUEST
      verifyLogWithHeaders(mockLogger, "error", "invalid api-subscription-fields-id NOT-A_UUID", request.headers.headers)
    }

    "return 400 if the request has no payload" in new Setup {
      when(mockFieldsService.getClientId(any[UUID])(any[HeaderCarrier])).thenReturn(Future.successful(Some(clientId)))
      val request = FakeRequest(POST, "/queue", Headers(SUBSCRIPTION_FIELDS_ID_HEADER_NAME -> fieldsId), AnyContentAsEmpty)

      val result = await(queueController.save()(request))

      status(result) shouldBe BAD_REQUEST
      verifyLogWithHeaders(mockLogger, "error", "missing body when saving", request.headers.headers)
    }

    "return 201 when getting client id via subscription fields id" in new Setup {
      private val xml = <xml>
        <node>Stuff</node>
      </xml>
      private val request = FakeRequest(POST, "/queue", Headers(SUBSCRIPTION_FIELDS_ID_HEADER_NAME -> fieldsId, CONTENT_TYPE -> XML, CONVERSATION_ID_HEADER_NAME -> "eaca01f9-ec3b-4ede-b263-61b626dde231"), AnyContentAsEmpty).withXmlBody(xml)
      private val notification = Notification(notificationId, ConversationId1Uuid, Map(CONTENT_TYPE -> XML), xml.toString(), DateTime.now(), None)
      when(mockQueueService.save(mockEq(clientId), any())).thenReturn(notification)
      when(mockFieldsService.getClientId(mockEq(UUID.fromString(fieldsId)))(any())).thenReturn(Some(clientId))

      val result = queueController.save()(request)

      status(result) shouldBe CREATED
      header(LOCATION, result) shouldBe Some(s"/notification/$notificationId")
      verify(mockUuidService, times(1)).uuid()
    }

    "use ids from from header when present" in new Setup {
      private val xml = <xml>
        <node>Stuff</node>
      </xml>
      private val request = FakeRequest(POST, "/queue", Headers(SUBSCRIPTION_FIELDS_ID_HEADER_NAME -> fieldsId,
        CONTENT_TYPE -> XML, CONVERSATION_ID_HEADER_NAME -> "eaca01f9-ec3b-4ede-b263-61b626dde231",
        NOTIFICATION_ID_HEADER_NAME -> notificationIdHeaderValue), AnyContentAsEmpty).withXmlBody(xml)
      private val notification = Notification(UUID.fromString(notificationIdHeaderValue), ConversationId1Uuid, Map(CONTENT_TYPE -> XML), xml.toString(), DateTime.now(), None)
      when(mockQueueService.save(mockEq(clientId), any())).thenReturn(notification)
      when(mockFieldsService.getClientId(mockEq(UUID.fromString(fieldsId)))(any())).thenReturn(Some(clientId))

      val result = queueController.save()(request)

      verify(mockUuidService, never()).uuid()
      header(LOCATION, result) shouldBe Some(s"/notification/53d1c27f-7af9-4310-8313-2e4f24766995")
    }

    "Propagate and log exceptions thrown in subscription fields id connector" in new Setup {
      private val xml = <xml>
        <node>Stuff</node>
      </xml>
      private val request = FakeRequest(POST, "/queue", Headers(SUBSCRIPTION_FIELDS_ID_HEADER_NAME -> fieldsId, CONTENT_TYPE -> XML, CONVERSATION_ID_HEADER_NAME -> "test-conversation-id"), AnyContentAsEmpty).withXmlBody(xml)
      private val notification = Notification(notificationId, ConversationId1Uuid, Map(CONTENT_TYPE -> XML), xml.toString(), DateTime.now(), None)
      when(mockQueueService.save(mockEq(clientId), any())).thenReturn(notification)
      when(mockFieldsService.getClientId(mockEq(UUID.fromString(fieldsId)))(any())).thenReturn(Future.failed(emulatedServiceFailure))

      await(queueController.save()(request))

      verifyLogWithHeaders(mockLogger, "error", "Error calling api-subscription-fields-service due to Emulated service failure.", request.headers.headers)
    }

  }

  "GET /notifications" should {

    "return 400 when the X-Client-ID header is not sent to the request" in new Setup {
      val result = await(queueController.getAllByClientId()(FakeRequest(GET, "/notifications")))

      status(result) shouldBe BAD_REQUEST
      verifyLogWithHeaders(mockLogger, "error", "missing X-Client-ID header when calling getAllByClientId endpoint", List((HOST, "localhost")))
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
      verifyLogWithHeaders(mockLogger, "info", "getting all notifications", request.headers.headers)
      verifyLogWithHeaders(mockLogger, "debug", s"listing all notifications $expectedJson", request.headers.headers)
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
      val result = await(queueController.get(notificationId)(FakeRequest(GET, s"/notification/$notificationId")))

      status(result) shouldBe BAD_REQUEST
    }

    "return 200" in new Setup {
      val payload = "<xml>a</xml>"
      when(mockQueueService.get(clientId, notificationId)).thenReturn(Future.successful(Some(Notification(notificationId, ConversationId1Uuid, Map(CONTENT_TYPE -> XML, CONVERSATION_ID_HEADER_NAME -> ConversationId1), payload, DateTime.now(), None))))
      val request = FakeRequest(GET, s"/notification/$notificationId", Headers(CLIENT_ID_HEADER_NAME -> clientId), AnyContentAsEmpty)

      val result = await(queueController.get(notificationId)(request))

      status(result) shouldBe OK
      bodyOf(result) shouldBe payload
      header(CONVERSATION_ID_HEADER_NAME, result) shouldBe Some("eaca01f9-ec3b-4ede-b263-61b626dde231")
      header(CLIENT_ID_HEADER_NAME, result) shouldBe None
      verifyLogWithHeaders(mockLogger, "info", s"getting notification id ${notificationId.toString}", request.headers.headers)
      verifyLogWithHeaders(mockLogger, "debug", s"found notification id ${notificationId.toString}", request.headers.headers)
    }

    "return 404 if the notification is not found" in new Setup {
      when(mockQueueService.get(clientId, notificationId)).thenReturn(Future.successful(None))
      val request = FakeRequest(GET, s"/notification/$notificationId", Headers(CLIENT_ID_HEADER_NAME -> clientId), AnyContentAsEmpty)

      val result = await(queueController.get(notificationId)(request))

      status(result) shouldBe NOT_FOUND
      bodyOf(result) shouldBe "NOT FOUND"
      verifyLogWithHeaders(mockLogger, "debug", s"requested notification id ${notificationId.toString} not found", request.headers.headers)
    }
  }

  "DELETE /notification/:id" should {

    "return 400 when the X-Client-ID header is not sent in the request" in new Setup {
      val result = await(queueController.delete(notificationId)(FakeRequest(DELETE, s"/notification/$notificationId")))

      status(result) shouldBe BAD_REQUEST
    }

    "return 204 if the notification is deleted" in new Setup {
      when(mockQueueService.delete(clientId, notificationId)).thenReturn(Future.successful(true))
      val request = FakeRequest(DELETE, s"/notification/$notificationId", Headers(CLIENT_ID_HEADER_NAME -> clientId), AnyContentAsEmpty)

      val result = await(queueController.delete(notificationId)(request))

      status(result) shouldBe NO_CONTENT
      verifyLogWithHeaders(mockLogger, "info", s"deleting notification id ${notificationId.toString}", request.headers.headers)
      verifyLogWithHeaders(mockLogger, "debug", s"successfully deleted notification id ${notificationId.toString}", request.headers.headers)
    }

    "return 404 if the notification is not found" in new Setup {
      when(mockQueueService.delete(clientId, notificationId)).thenReturn(Future.successful(false))
      val request = FakeRequest(DELETE, s"/notification/$notificationId", Headers(CLIENT_ID_HEADER_NAME -> clientId), AnyContentAsEmpty)

      val result = await(queueController.delete(notificationId)(request))

      status(result) shouldBe NOT_FOUND
      bodyOf(result) shouldBe "NOT FOUND"
      verifyLogWithHeaders(mockLogger, "debug", s"nothing to delete for notification id ${notificationId.toString}", request.headers.headers)
    }
  }

  private def verifyLogWithHeaders(logger: NotificationLogger, method: String, message: String, headers: SeqOfHeader): Unit = {
    PassByNameVerifier(logger, method)
      .withByNameParam(message)
      .withByNameParam(headers)
      .verify()
  }

}
