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

import org.mockito.Mockito._
import org.scalatestplus.mockito.MockitoSugar
import play.api.http.ContentTypes.XML
import play.api.http.HeaderNames.{CONTENT_TYPE, HOST}
import play.api.mvc.request.RequestTarget
import play.api.mvc.{AnyContentAsEmpty, ControllerComponents, Headers, Result}
import play.api.test.Helpers._
import play.api.test.{FakeRequest, Helpers}
import uk.gov.hmrc.apinotificationqueue.controller.{DateTimeProvider, EnhancedNotificationsController}
import uk.gov.hmrc.apinotificationqueue.logging.NotificationLogger
import uk.gov.hmrc.apinotificationqueue.model.NotificationStatus._
import uk.gov.hmrc.apinotificationqueue.model.{Notification, NotificationId, NotificationWithIdOnly, SeqOfHeader}
import uk.gov.hmrc.apinotificationqueue.service.{ApiSubscriptionFieldsService, QueueService, UuidService}
import util.MockitoPassByNameHelper.PassByNameVerifier
import util.TestData.{ConversationId1, ConversationId1Uuid, NotificationWithIdAndPulledStatus1, NotificationWithIdAndPulledStatus2}
import util.XmlUtil.string2xml
import util.{MaterializerSupport, UnitSpec}

import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID
import scala.concurrent.{ExecutionContext, Future}
import scala.xml.Utility.trim

class EnhancedNotificationsControllerSpec extends UnitSpec with MaterializerSupport with MockitoSugar {

  private implicit val ec: ExecutionContext = Helpers.stubControllerComponents().executionContext

  private val CLIENT_ID_HEADER_NAME = "x-client-id"
  private val CONVERSATION_ID_HEADER_NAME = "X-Conversation-ID"

  private val alreadyPulledError = trim(
    <errorResponse>
      <code>BAD_REQUEST</code>
      <message>Notification has been pulled</message>
    </errorResponse>
  )

  private val missingClientIdError = trim(
    <errorResponse>
      <code>BAD_REQUEST</code>
      <message>X-Client-ID required</message>
   </errorResponse>
  )

  private val notFoundError = trim(
    <errorResponse>
      <code>NOT_FOUND</code>
      <message>Resource was not found</message>
   </errorResponse>
  )

  private val unpulledError = trim(
    <errorResponse>
      <code>BAD_REQUEST</code>
      <message>Notification is unpulled</message>
    </errorResponse>
  )

  trait Setup {

    protected val clientId = "abc123"
    protected val uuid: UUID = UUID.fromString("7c422a91-1df6-439c-b561-f2cf2d8978ef")

    val notificationWithIdOnly1 = NotificationWithIdOnly(NotificationId(UUID.randomUUID()))
    val notificationWithIdOnly2 = NotificationWithIdOnly(NotificationId(UUID.randomUUID()))

    protected val mockQueueService: QueueService = mock[QueueService]
    protected val mockFieldsService: ApiSubscriptionFieldsService = mock[ApiSubscriptionFieldsService]
    protected val mockUuidService: UuidService = mock[UuidService]
    protected val mockLogger: NotificationLogger = mock[NotificationLogger]
    protected val mockDateTimeProvider: DateTimeProvider = mock[DateTimeProvider]
    protected val controllerComponents: ControllerComponents = Helpers.stubControllerComponents()
    protected val controller = new EnhancedNotificationsController(mockQueueService, mockFieldsService, mockUuidService, mockDateTimeProvider, controllerComponents, mockLogger)
    protected val payload = "<xml>a</xml>"
    protected val unpulledNotification = Notification(uuid, ConversationId1Uuid, Map(CONTENT_TYPE -> XML, CONVERSATION_ID_HEADER_NAME -> ConversationId1), payload, Instant.now(), None)
    protected val time = Instant.now()
    protected val pulledNotification = unpulledNotification.copy(datePulled = Some(time))

    when(mockDateTimeProvider.now()).thenReturn(time)
    when(mockUuidService.uuid()).thenReturn(uuid)
    protected val unpulledRequest = FakeRequest(GET, s"/notifications/unpulled/$uuid", Headers(CLIENT_ID_HEADER_NAME -> clientId), AnyContentAsEmpty)
    protected val pulledRequest = FakeRequest(GET, s"/notifications/pulled/$uuid", Headers(CLIENT_ID_HEADER_NAME -> clientId), AnyContentAsEmpty)
    protected val conversationEndpoint = s"/notifications/conversationId/$ConversationId1"
    protected val conversationIdRequest = FakeRequest(GET, conversationEndpoint, Headers(CLIENT_ID_HEADER_NAME -> clientId), AnyContentAsEmpty)
  }

  "GET /notifications/unpulled/:id" should {

    "return 200" in new Setup {
      when(mockQueueService.get(clientId, uuid)).thenReturn(Future.successful(Some(unpulledNotification)))

      val result: Result = await(controller.getUnpulledByNotificationId(uuid)(unpulledRequest))

      status(result) shouldBe OK
      bodyOf(result) shouldBe payload
      verify(mockQueueService).update(clientId, pulledNotification)
      header(CONVERSATION_ID_HEADER_NAME, result) shouldBe Some("eaca01f9-ec3b-4ede-b263-61b626dde231")
      header(CLIENT_ID_HEADER_NAME, result) shouldBe None
      verifyLogWithHeaders(mockLogger, "info", "getting unpulled notificationId 7c422a91-1df6-439c-b561-f2cf2d8978ef", unpulledRequest.headers.headers)
      verifyLogWithHeaders(mockLogger, "debug", "Pulling unpulled notification for conversationId: eaca01f9-ec3b-4ede-b263-61b626dde231 with notificationId: 7c422a91-1df6-439c-b561-f2cf2d8978ef", unpulledRequest.headers.headers)

    }

    "return 400 if requested notification has already been pulled" in new Setup {
      private val minutes = 10
      when(mockQueueService.get(clientId, uuid)).thenReturn(Future.successful(
        Some(Notification(uuid, ConversationId1Uuid, Map(CONTENT_TYPE -> XML, CONVERSATION_ID_HEADER_NAME -> ConversationId1), payload, Instant.now(), Some(Instant.now().minus(minutes, ChronoUnit.MINUTES ))))))

      val result: Result = await(controller.getUnpulledByNotificationId(uuid)(unpulledRequest))

      status(result) shouldBe BAD_REQUEST
      string2xml(contentAsString(result)) shouldBe alreadyPulledError
      verifyLogWithHeaders(mockLogger, "error", "Notification has been pulled for id 7c422a91-1df6-439c-b561-f2cf2d8978ef", unpulledRequest.headers.headers)
    }

    "return 400 when the X-Client-ID header is not present in the request" in new Setup {
      val request = FakeRequest(GET, s"/notifications/unpulled/$uuid")
      val result: Result = await(controller.getUnpulledByNotificationId(uuid)(request))

      status(result) shouldBe BAD_REQUEST
      string2xml(contentAsString(result)) shouldBe missingClientIdError
      verifyLogWithHeaders(mockLogger, "error", "missing X-Client-ID header when calling get unpulled endpoint", request.headers.headers)
    }

    "return 404 if the notification is not found" in new Setup {
      when(mockQueueService.get(clientId, uuid)).thenReturn(Future.successful(None))

      val result: Result = await(controller.getUnpulledByNotificationId(uuid)(unpulledRequest))

      status(result) shouldBe NOT_FOUND
      string2xml(contentAsString(result)) shouldBe notFoundError
      verifyLogWithHeaders(mockLogger, "error", "Notification not found for id 7c422a91-1df6-439c-b561-f2cf2d8978ef", unpulledRequest.headers.headers)
    }
  }

  "GET /notifications/pulled/:id" should {

    "return 200" in new Setup {
      when(mockQueueService.get(clientId, uuid)).thenReturn(Future.successful(Some(pulledNotification)))

      val result: Result = await(controller.getPulledByNotificationId(uuid)(pulledRequest))

      status(result) shouldBe OK
      bodyOf(result) shouldBe payload
      header(CONVERSATION_ID_HEADER_NAME, result) shouldBe Some("eaca01f9-ec3b-4ede-b263-61b626dde231")
      header(CLIENT_ID_HEADER_NAME, result) shouldBe None
      verifyLogWithHeaders(mockLogger, "info", "getting pulled notificationId 7c422a91-1df6-439c-b561-f2cf2d8978ef", unpulledRequest.headers.headers)
      verifyLogWithHeaders(mockLogger, "debug", "Pulling pulled notification for conversationId: eaca01f9-ec3b-4ede-b263-61b626dde231 with notificationId: 7c422a91-1df6-439c-b561-f2cf2d8978ef", pulledRequest.headers.headers)
    }

    "return 400 if requested notification is unpulled" in new Setup {
      when(mockQueueService.get(clientId, uuid)).thenReturn(Future.successful(Some(Notification(uuid, ConversationId1Uuid, Map(CONTENT_TYPE -> XML, CONVERSATION_ID_HEADER_NAME -> ConversationId1), payload, Instant.now(), None))))

      val result: Result = await(controller.getPulledByNotificationId(uuid)(pulledRequest))

      status(result) shouldBe BAD_REQUEST
      string2xml(contentAsString(result)) shouldBe unpulledError
      verifyLogWithHeaders(mockLogger, "error", "Notification is unpulled for id 7c422a91-1df6-439c-b561-f2cf2d8978ef", pulledRequest.headers.headers)
    }

    "return 400 when the X-Client-ID header is not present in the request" in new Setup {
      val result: Result = await(controller.getPulledByNotificationId(uuid)(FakeRequest(GET, s"/notifications/pulled/$uuid")))

      status(result) shouldBe BAD_REQUEST
      string2xml(contentAsString(result)) shouldBe missingClientIdError
      verifyLogWithHeaders(mockLogger, "error", s"missing X-Client-ID header when calling get pulled endpoint", List((HOST, "localhost")))
    }

    "return 404 if the notification is not found" in new Setup {
      when(mockQueueService.get(clientId, uuid)).thenReturn(Future.successful(None))

      val result: Result = await(controller.getPulledByNotificationId(uuid)(pulledRequest))

      status(result) shouldBe NOT_FOUND
      string2xml(contentAsString(result)) shouldBe notFoundError
      verifyLogWithHeaders(mockLogger, "error", "Notification not found for id 7c422a91-1df6-439c-b561-f2cf2d8978ef", pulledRequest.headers.headers)
    }
  }

  "GET /notifications/pulled" should {

    "return 400 when the X-Client-ID header is not sent to the request" in new Setup {
      val result = await(controller.getAllPulledByClientId()(FakeRequest(GET, "/notifications/pulled")))

      status(result) shouldBe BAD_REQUEST
      verifyLogWithHeaders(mockLogger, "error", "missing X-Client-ID header when calling get all pulled by client id endpoint", List((HOST, "localhost")))
    }

    "return 200" in new Setup {
      when(mockQueueService.get(clientId, Some(Pulled))).thenReturn(Future.successful(List(notificationWithIdOnly1, notificationWithIdOnly2)))

      val request = FakeRequest(GET, "/notifications/pulled", Headers(CLIENT_ID_HEADER_NAME -> clientId), AnyContentAsEmpty)
      val result = await(controller.getAllPulledByClientId()(request))

      status(result) shouldBe OK

      val expectedJson = s"""{"notifications":["/notifications/pulled/${notificationWithIdOnly1.notification.notificationId.toString}","/notifications/pulled/${notificationWithIdOnly2.notification.notificationId.toString}"]}"""
      bodyOf(result) shouldBe expectedJson
      verifyLogWithHeaders(mockLogger, "debug", s"returning notifications $expectedJson", request.headers.headers)
      verifyLogWithHeaders(mockLogger, "info", s"listing pulled notifications by clientId", request.headers.headers)
    }

    "return empty list if there are no notifications for a specific client id" in new Setup {
      when(mockQueueService.get(clientId, Some(Pulled))).thenReturn(Future.successful(List()))

      val request = FakeRequest(GET, "/notifications/pulled", Headers(CLIENT_ID_HEADER_NAME -> clientId), AnyContentAsEmpty)

      val result = await(controller.getAllPulledByClientId()(request))

      status(result) shouldBe OK
      bodyOf(result) shouldBe """{"notifications":[]}"""
    }
  }

  "GET /notifications/unpulled" should {

    "return 400 when the X-Client-ID header is not sent to the request" in new Setup {
      val result = await(controller.getAllPulledByClientId()(FakeRequest(GET, "/notifications/unpulled")))

      status(result) shouldBe BAD_REQUEST
    }

    "return 200" in new Setup {
      when(mockQueueService.get(clientId, Some(Unpulled))).thenReturn(Future.successful(List(notificationWithIdOnly1, notificationWithIdOnly2)))

      val request = FakeRequest(GET, "/notifications/unpulled", Headers(CLIENT_ID_HEADER_NAME -> clientId), AnyContentAsEmpty)
      val result = await(controller.getAllUnpulledByClientId()(request))

      status(result) shouldBe OK

      val expectedJson = s"""{"notifications":["/notifications/unpulled/${notificationWithIdOnly1.notification.notificationId.toString}","/notifications/unpulled/${notificationWithIdOnly2.notification.notificationId.toString}"]}"""
      bodyOf(result) shouldBe expectedJson
      verifyLogWithHeaders(mockLogger, "info", s"listing unpulled notifications by clientId", request.headers.headers)
    }

    "return empty list if there are no notifications for a specific client id" in new Setup {
      when(mockQueueService.get(clientId, Some(Unpulled))).thenReturn(Future.successful(List()))

      val request = FakeRequest(GET, "/notifications/unpulled", Headers(CLIENT_ID_HEADER_NAME -> clientId), AnyContentAsEmpty)

      val result = await(controller.getAllUnpulledByClientId()(request))

      status(result) shouldBe OK
      bodyOf(result) shouldBe """{"notifications":[]}"""
    }
  }
  
  "GET /notifications/conversationId/:id" should {

    "return 400 when the X-Client-ID header is not sent to the request" in new Setup {
      val result = await(controller.getAllByConversationId(ConversationId1Uuid)(FakeRequest(GET, s"/notifications/conversationId/$ConversationId1")))

      status(result) shouldBe BAD_REQUEST
    }

    "return 200" in new Setup {
      when(mockQueueService.getByConversationId(clientId, UUID.fromString(ConversationId1)))
        .thenReturn(Future.successful(List(NotificationWithIdAndPulledStatus1, NotificationWithIdAndPulledStatus2)))

      val result = await(controller.getAllByConversationId(ConversationId1Uuid)(conversationIdRequest))

      status(result) shouldBe OK
      
      val expectedJson = s"""{"notifications":["/notifications/unpulled/ea52e86c-3322-4a5b-8bf7-b2d7d6e3fa8d","/notifications/pulled/5d60bab0-b866-4179-ba5c-b8e19176cfd9"]}"""
      bodyOf(result) shouldBe expectedJson
      verifyLogWithHeaders(mockLogger, "info", "listing notifications with conversationId eaca01f9-ec3b-4ede-b263-61b626dde231", conversationIdRequest.headers.headers)
    }

    "return empty list if there are no notifications for a specific conversationId" in new Setup {
      when(mockQueueService.getByConversationId(clientId, ConversationId1Uuid)).thenReturn(Future.successful(List()))

      val result = await(controller.getAllByConversationId(ConversationId1Uuid)(conversationIdRequest))

      status(result) shouldBe OK
      bodyOf(result) shouldBe """{"notifications":[]}"""
    }
  }
  
  "GET /notifications/conversationId/:id/pulled" should {

    "return 400 when the X-Client-ID header is not sent to the request" in new Setup {
      val result = await(controller.getAllPulledByConversationId(ConversationId1Uuid)(FakeRequest(GET, s"/notifications/conversationId/$ConversationId1/pulled")))

      status(result) shouldBe BAD_REQUEST
    }

    "return 200" in new Setup {
      when(mockQueueService.getByConversationId(clientId, UUID.fromString(ConversationId1), Pulled))
        .thenReturn(Future.successful(List(notificationWithIdOnly1, notificationWithIdOnly2)))

      val result = await(controller.getAllPulledByConversationId(ConversationId1Uuid)(conversationIdRequest.withTarget(RequestTarget(s"$conversationEndpoint/pulled", conversationIdRequest.path, conversationIdRequest.queryString))))

      status(result) shouldBe OK

      val expectedJson = s"""{"notifications":["/notifications/pulled/${notificationWithIdOnly1.notification.notificationId.toString}","/notifications/pulled/${notificationWithIdOnly2.notification.notificationId.toString}"]}"""
      bodyOf(result) shouldBe expectedJson
      verifyLogWithHeaders(mockLogger, "info", "listing pulled notifications by conversationId eaca01f9-ec3b-4ede-b263-61b626dde231", conversationIdRequest.headers.headers)
    }

    "return empty list if there are no notifications for a specific conversationId and status" in new Setup {
      when(mockQueueService.getByConversationId(clientId, ConversationId1Uuid, Pulled)).thenReturn(Future.successful(List()))

      val result = await(controller.getAllPulledByConversationId(ConversationId1Uuid)(conversationIdRequest.withTarget(RequestTarget(s"$conversationEndpoint/pulled", conversationIdRequest.path, conversationIdRequest.queryString))))

      status(result) shouldBe OK
      bodyOf(result) shouldBe """{"notifications":[]}"""
    }
  }
  
  "GET /notifications/conversationId/:id/unpulled" should {

    "return 400 when the X-Client-ID header is not sent to the request" in new Setup {
      val result = await(controller.getAllPulledByConversationId(ConversationId1Uuid)(FakeRequest(GET, s"/notifications/conversationId/$ConversationId1/unpulled")))

      status(result) shouldBe BAD_REQUEST
    }

    "return 200" in new Setup {
      when(mockQueueService.getByConversationId(clientId, UUID.fromString(ConversationId1), Unpulled))
        .thenReturn(Future.successful(List(notificationWithIdOnly1, notificationWithIdOnly2)))

      val result = await(controller.getAllUnpulledByConversationId(ConversationId1Uuid)(conversationIdRequest.withTarget(RequestTarget(s"$conversationEndpoint/unpulled", conversationIdRequest.path, conversationIdRequest.queryString))))

      status(result) shouldBe OK

      val expectedJson = s"""{"notifications":["/notifications/unpulled/${notificationWithIdOnly1.notification.notificationId.toString}","/notifications/unpulled/${notificationWithIdOnly2.notification.notificationId.toString}"]}"""
      bodyOf(result) shouldBe expectedJson
      verifyLogWithHeaders(mockLogger, "info", "listing unpulled notifications by conversationId eaca01f9-ec3b-4ede-b263-61b626dde231", conversationIdRequest.headers.headers)
    }

    "return empty list if there are no notifications for a specific conversationId and status" in new Setup {
      when(mockQueueService.getByConversationId(clientId, ConversationId1Uuid, Unpulled)).thenReturn(Future.successful(List()))

      val result = await(controller.getAllUnpulledByConversationId(ConversationId1Uuid)(conversationIdRequest.withTarget(RequestTarget(s"$conversationEndpoint/unpulled", conversationIdRequest.path, conversationIdRequest.queryString))))

      status(result) shouldBe OK
      bodyOf(result) shouldBe """{"notifications":[]}"""
    }
  }

  private def verifyLogWithHeaders(logger: NotificationLogger, method: String, message: String, headers: SeqOfHeader): Unit = {
    PassByNameVerifier(logger, method)
      .withByNameParam(message)
      .withByNameParam(headers)
      .verify()
  }

}
