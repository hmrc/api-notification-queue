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
import uk.gov.hmrc.apinotificationqueue.model.Notification
import uk.gov.hmrc.apinotificationqueue.service.{ApiSubscriptionFieldsService, QueueService}
import uk.gov.hmrc.customs.api.common.logging.CdsLogger
import uk.gov.hmrc.play.test.{UnitSpec, WithFakeApplication}
import util.MockitoPassByNameHelper.PassByNameVerifier
import util.XmlUtil.string2xml
import scala.xml.Utility.trim

import scala.concurrent.Future

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

    protected val notification1 = Notification(UUID.randomUUID(), Map.empty, "<xml></xml>", DateTime.now(), None)
    protected val notification2: Notification = notification1.copy(notificationId = UUID.randomUUID())

    class StaticIDGenerator extends NotificationIdGenerator {
      override def generateId(): UUID = uuid
    }

    protected val mockQueueService: QueueService = mock[QueueService]
    protected val mockFieldsService: ApiSubscriptionFieldsService = mock[ApiSubscriptionFieldsService]
    protected val mockCdsLogger: CdsLogger = mock[CdsLogger]
    protected val mockDateTimeProvider: DateTimeProvider = mock[DateTimeProvider]
    protected val controller = new EnhancedNotificationsController(mockQueueService, mockFieldsService, new StaticIDGenerator, mockDateTimeProvider, mockCdsLogger)
    protected val payload = "<xml>a</xml>"
    protected val unpulledNotification = Notification(uuid, Map(CONTENT_TYPE -> XML, CONVERSATION_ID_HEADER_NAME -> "5"), payload, DateTime.now(), None)
    protected val time = DateTime.now()
    protected val pulledNotification = unpulledNotification.copy(datePulled = Some(time))

    when(mockDateTimeProvider.now()).thenReturn(time)
    protected val request = FakeRequest(GET, s"/notifications/unpulled/$uuid", Headers(CLIENT_ID_HEADER_NAME -> clientId), AnyContentAsEmpty)
  }

  "GET /notifications/unpulled/:id" should {

    "return 200" in new Setup {
      when(mockQueueService.get(clientId, uuid)).thenReturn(Future.successful(Some(unpulledNotification)))

      val result: Result = await(controller.unpulled(uuid)(request))

      status(result) shouldBe OK
      bodyOf(result) shouldBe payload
      verify(mockQueueService).update(clientId, pulledNotification)
      header(CONVERSATION_ID_HEADER_NAME, result) shouldBe Some("5")
      header(CLIENT_ID_HEADER_NAME, result) shouldBe None
    }

    "return 400 if requested notification has already been pulled" in new Setup {
      private val minutes = 10
      when(mockQueueService.get(clientId, uuid)).thenReturn(Future.successful(
        Some(Notification(uuid, Map(CONTENT_TYPE -> XML, CONVERSATION_ID_HEADER_NAME -> "5"), payload, DateTime.now(), Some(DateTime.now().minusMinutes(minutes))))))

      val result: Result = await(controller.unpulled(uuid)(request))

      status(result) shouldBe BAD_REQUEST
      string2xml(contentAsString(result)) shouldBe alreadyPulledError
      PassByNameVerifier(mockCdsLogger, "error")
        .withByNameParam("Notification has been pulled for id: 7c422a91-1df6-439c-b561-f2cf2d8978ef")
        .verify()

    }

    "return 400 when the X-Client-ID header is not present in the request" in new Setup {
      val result: Result = await(controller.unpulled(uuid)(FakeRequest(GET, s"/notifications/unpulled/$uuid")))

      status(result) shouldBe BAD_REQUEST
      string2xml(contentAsString(result)) shouldBe missingClientIdError
      PassByNameVerifier(mockCdsLogger, "error")
        .withByNameParam("Client id is missing")
        .verify()

    }

    "return 404 if the notification is not found" in new Setup {
      when(mockQueueService.get(clientId, uuid)).thenReturn(Future.successful(None))

      val result: Result = await(controller.unpulled(uuid)(request))

      status(result) shouldBe NOT_FOUND
      string2xml(contentAsString(result)) shouldBe notFoundError
      PassByNameVerifier(mockCdsLogger, "error")
        .withByNameParam("Notification not found for id: 7c422a91-1df6-439c-b561-f2cf2d8978ef")
        .verify()
    }
  }

  "GET /notifications/pulled/:id" should {

    "return 200" in new Setup {
      when(mockQueueService.get(clientId, uuid)).thenReturn(Future.successful(Some(pulledNotification)))

      val result: Result = await(controller.pulled(uuid)(request))

      status(result) shouldBe OK
      bodyOf(result) shouldBe payload
      header(CONVERSATION_ID_HEADER_NAME, result) shouldBe Some("5")
      header(CLIENT_ID_HEADER_NAME, result) shouldBe None
    }

    "return 400 if requested notification is unpulled" in new Setup {
      when(mockQueueService.get(clientId, uuid)).thenReturn(Future.successful(Some(Notification(uuid, Map(CONTENT_TYPE -> XML, CONVERSATION_ID_HEADER_NAME -> "5"), payload, DateTime.now(), None))))

      val result: Result = await(controller.pulled(uuid)(request))

      status(result) shouldBe BAD_REQUEST
      string2xml(contentAsString(result)) shouldBe unpulledError
      PassByNameVerifier(mockCdsLogger, "error")
        .withByNameParam("Notification is unpulled for id: 7c422a91-1df6-439c-b561-f2cf2d8978ef")
        .verify()

    }

    "return 400 when the X-Client-ID header is not present in the request" in new Setup {
      val result: Result = await(controller.pulled(uuid)(FakeRequest(GET, s"/notifications/pulled/$uuid")))

      status(result) shouldBe BAD_REQUEST
      string2xml(contentAsString(result)) shouldBe missingClientIdError
      PassByNameVerifier(mockCdsLogger, "error")
        .withByNameParam("Client id is missing")
        .verify()

    }

    "return 404 if the notification is not found" in new Setup {
      when(mockQueueService.get(clientId, uuid)).thenReturn(Future.successful(None))

      val result: Result = await(controller.pulled(uuid)(request))

      status(result) shouldBe NOT_FOUND
      string2xml(contentAsString(result)) shouldBe notFoundError
      PassByNameVerifier(mockCdsLogger, "error")
        .withByNameParam("Notification not found for id: 7c422a91-1df6-439c-b561-f2cf2d8978ef")
        .verify()
    }
  }
}
