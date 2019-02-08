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

import akka.stream.Materializer
import org.joda.time.DateTime
import org.mockito.Mockito._
import org.scalatest.mockito.MockitoSugar
import play.api.http.ContentTypes.XML
import play.api.http.HeaderNames.CONTENT_TYPE
import play.api.mvc.{AnyContentAsEmpty, Headers, Result}
import play.api.test.FakeRequest
import play.api.test.Helpers._
import uk.gov.hmrc.apinotificationqueue.controller.{DateTimeProvider, EnhancedNotificationsController, NotificationIdGenerator}
import uk.gov.hmrc.apinotificationqueue.logging.NotificationLogger
import uk.gov.hmrc.apinotificationqueue.model.NotificationStatus._
import uk.gov.hmrc.apinotificationqueue.model.{Notification, NotificationId, NotificationWithIdOnly, SeqOfHeader}
import uk.gov.hmrc.apinotificationqueue.service.{ApiSubscriptionFieldsService, QueueService}
import uk.gov.hmrc.play.test.{UnitSpec, WithFakeApplication}
import util.MockitoPassByNameHelper.PassByNameVerifier
import util.XmlUtil.string2xml

import scala.concurrent.Future
import scala.xml.Utility.trim

class EnhancedNotificationsControllerSpec extends UnitSpec with MockitoSugar with WithFakeApplication {

  private implicit lazy val materializer: Materializer = fakeApplication.materializer

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

    class StaticIDGenerator extends NotificationIdGenerator {
      override def generateId(): UUID = uuid
    }

    protected val mockQueueService: QueueService = mock[QueueService]
    protected val mockFieldsService: ApiSubscriptionFieldsService = mock[ApiSubscriptionFieldsService]
    protected val mockLogger: NotificationLogger = mock[NotificationLogger]
    protected val mockDateTimeProvider: DateTimeProvider = mock[DateTimeProvider]
    protected val controller = new EnhancedNotificationsController(mockQueueService, mockFieldsService, new StaticIDGenerator, mockDateTimeProvider, mockLogger)
    protected val payload = "<xml>a</xml>"
    protected val unpulledNotification = Notification(uuid, Map(CONTENT_TYPE -> XML, CONVERSATION_ID_HEADER_NAME -> "5"), payload, DateTime.now(), None)
    protected val time = DateTime.now()
    protected val pulledNotification = unpulledNotification.copy(datePulled = Some(time))

    when(mockDateTimeProvider.now()).thenReturn(time)
    protected val unpulledRequest = FakeRequest(GET, s"/notifications/unpulled/$uuid", Headers(CLIENT_ID_HEADER_NAME -> clientId), AnyContentAsEmpty)
    protected val pulledRequest = FakeRequest(GET, s"/notifications/pulled/$uuid", Headers(CLIENT_ID_HEADER_NAME -> clientId), AnyContentAsEmpty)
  }

  "GET /notifications/unpulled/:id" should {

    "return 200" in new Setup {
      when(mockQueueService.get(clientId, uuid)).thenReturn(Future.successful(Some(unpulledNotification)))

      val result: Result = await(controller.unpulled(uuid)(unpulledRequest))

      status(result) shouldBe OK
      bodyOf(result) shouldBe payload
      verify(mockQueueService).update(clientId, pulledNotification)
      header(CONVERSATION_ID_HEADER_NAME, result) shouldBe Some("5")
      header(CLIENT_ID_HEADER_NAME, result) shouldBe None
      verifyLogWithHeaders(mockLogger, "info", "getting unpulled notification id 7c422a91-1df6-439c-b561-f2cf2d8978ef", unpulledRequest.headers.headers)
      verifyLogWithHeaders(mockLogger, "debug", "Pulling unpulled notification for id 7c422a91-1df6-439c-b561-f2cf2d8978ef", unpulledRequest.headers.headers)
    }

    "return 400 if requested notification has already been pulled" in new Setup {
      private val minutes = 10
      when(mockQueueService.get(clientId, uuid)).thenReturn(Future.successful(
        Some(Notification(uuid, Map(CONTENT_TYPE -> XML, CONVERSATION_ID_HEADER_NAME -> "5"), payload, DateTime.now(), Some(DateTime.now().minusMinutes(minutes))))))

      val result: Result = await(controller.unpulled(uuid)(unpulledRequest))

      status(result) shouldBe BAD_REQUEST
      string2xml(contentAsString(result)) shouldBe alreadyPulledError
      verifyLogWithHeaders(mockLogger, "error", "Notification has been pulled for id 7c422a91-1df6-439c-b561-f2cf2d8978ef", unpulledRequest.headers.headers)
    }

    "return 400 when the X-Client-ID header is not present in the request" in new Setup {
      val request = FakeRequest(GET, s"/notifications/unpulled/$uuid")
      val result: Result = await(controller.unpulled(uuid)(request))

      status(result) shouldBe BAD_REQUEST
      string2xml(contentAsString(result)) shouldBe missingClientIdError
      verifyLogWithHeaders(mockLogger, "error", "missing X-Client-ID header when calling get unpulled endpoint", request.headers.headers)
    }

    "return 404 if the notification is not found" in new Setup {
      when(mockQueueService.get(clientId, uuid)).thenReturn(Future.successful(None))

      val result: Result = await(controller.unpulled(uuid)(unpulledRequest))

      status(result) shouldBe NOT_FOUND
      string2xml(contentAsString(result)) shouldBe notFoundError
      verifyLogWithHeaders(mockLogger, "error", "Notification not found for id 7c422a91-1df6-439c-b561-f2cf2d8978ef", unpulledRequest.headers.headers)
    }
  }

  "GET /notifications/pulled/:id" should {

    "return 200" in new Setup {
      when(mockQueueService.get(clientId, uuid)).thenReturn(Future.successful(Some(pulledNotification)))

      val result: Result = await(controller.pulled(uuid)(pulledRequest))

      status(result) shouldBe OK
      bodyOf(result) shouldBe payload
      header(CONVERSATION_ID_HEADER_NAME, result) shouldBe Some("5")
      header(CLIENT_ID_HEADER_NAME, result) shouldBe None
      verifyLogWithHeaders(mockLogger, "info", "getting pulled notification id 7c422a91-1df6-439c-b561-f2cf2d8978ef", unpulledRequest.headers.headers)
      verifyLogWithHeaders(mockLogger, "debug", "Pulling pulled notification for id 7c422a91-1df6-439c-b561-f2cf2d8978ef", pulledRequest.headers.headers)
    }

    "return 400 if requested notification is unpulled" in new Setup {
      when(mockQueueService.get(clientId, uuid)).thenReturn(Future.successful(Some(Notification(uuid, Map(CONTENT_TYPE -> XML, CONVERSATION_ID_HEADER_NAME -> "5"), payload, DateTime.now(), None))))

      val result: Result = await(controller.pulled(uuid)(pulledRequest))

      status(result) shouldBe BAD_REQUEST
      string2xml(contentAsString(result)) shouldBe unpulledError
      verifyLogWithHeaders(mockLogger, "error", "Notification is unpulled for id 7c422a91-1df6-439c-b561-f2cf2d8978ef", pulledRequest.headers.headers)
    }

    "return 400 when the X-Client-ID header is not present in the request" in new Setup {
      val result: Result = await(controller.pulled(uuid)(FakeRequest(GET, s"/notifications/pulled/$uuid")))

      status(result) shouldBe BAD_REQUEST
      string2xml(contentAsString(result)) shouldBe missingClientIdError
      verifyLogWithHeaders(mockLogger, "error", s"missing X-Client-ID header when calling get pulled endpoint", Seq.empty)
    }

    "return 404 if the notification is not found" in new Setup {
      when(mockQueueService.get(clientId, uuid)).thenReturn(Future.successful(None))

      val result: Result = await(controller.pulled(uuid)(pulledRequest))

      status(result) shouldBe NOT_FOUND
      string2xml(contentAsString(result)) shouldBe notFoundError
      verifyLogWithHeaders(mockLogger, "error", "Notification not found for id 7c422a91-1df6-439c-b561-f2cf2d8978ef", pulledRequest.headers.headers)
    }
  }

  "GET /notifications/pulled" should {

    "return 400 when the X-Client-ID header is not sent to the request" in new Setup {
      val result = await(controller.getPulledByClientId()(FakeRequest(GET, "/notifications/pulled")))

      status(result) shouldBe BAD_REQUEST
      verifyLogWithHeaders(mockLogger, "error", "missing X-Client-ID header when calling get pulled by client id endpoint", Seq.empty)
    }

    "return 200" in new Setup {
      when(mockQueueService.get(clientId, Some(Pulled))).thenReturn(Future.successful(List(notificationWithIdOnly1, notificationWithIdOnly2)))

      val request = FakeRequest(GET, "/notifications/pulled", Headers(CLIENT_ID_HEADER_NAME -> clientId), AnyContentAsEmpty)
      val result = await(controller.getPulledByClientId()(request))

      status(result) shouldBe OK

      val expectedJson = s"""{"notifications":["/notifications/pulled/${notificationWithIdOnly1.notification.notificationId.toString}","/notifications/pulled/${notificationWithIdOnly2.notification.notificationId.toString}"]}"""
      bodyOf(result) shouldBe expectedJson
      verifyLogWithHeaders(mockLogger, "debug", s"listing pulled notifications $expectedJson", request.headers.headers)
      verifyLogWithHeaders(mockLogger, "info", s"listing pulled notifications", request.headers.headers)
    }

    "return empty list if there are no notifications for a specific client id" in new Setup {
      when(mockQueueService.get(clientId, Some(Pulled))).thenReturn(Future.successful(List()))

      val request = FakeRequest(GET, "/notifications/pulled", Headers(CLIENT_ID_HEADER_NAME -> clientId), AnyContentAsEmpty)

      val result = await(controller.getPulledByClientId()(request))

      status(result) shouldBe OK
      bodyOf(result) shouldBe """{"notifications":[]}"""
    }
  }

  "GET /notifications/unpulled" should {

    "return 400 when the X-Client-ID header is not sent to the request" in new Setup {
      val result = await(controller.getPulledByClientId()(FakeRequest(GET, "/notifications/unpulled")))

      status(result) shouldBe BAD_REQUEST
    }

    "return 200" in new Setup {
      when(mockQueueService.get(clientId, Some(Unpulled))).thenReturn(Future.successful(List(notificationWithIdOnly1, notificationWithIdOnly2)))

      val request = FakeRequest(GET, "/notifications/unpulled", Headers(CLIENT_ID_HEADER_NAME -> clientId), AnyContentAsEmpty)
      val result = await(controller.getUnpulledByClientId()(request))

      status(result) shouldBe OK

      val expectedJson = s"""{"notifications":["/notifications/unpulled/${notificationWithIdOnly1.notification.notificationId.toString}","/notifications/unpulled/${notificationWithIdOnly2.notification.notificationId.toString}"]}"""
      bodyOf(result) shouldBe expectedJson
      verifyLogWithHeaders(mockLogger, "info", s"listing unpulled notifications", request.headers.headers)
    }

    "return empty list if there are no notifications for a specific client id" in new Setup {
      when(mockQueueService.get(clientId, Some(Unpulled))).thenReturn(Future.successful(List()))

      val request = FakeRequest(GET, "/notifications/unpulled", Headers(CLIENT_ID_HEADER_NAME -> clientId), AnyContentAsEmpty)

      val result = await(controller.getUnpulledByClientId()(request))

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
