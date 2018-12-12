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

import scala.concurrent.Future

class EnhancedNotificationsControllerSpec extends UnitSpec with MockitoSugar with WithFakeApplication {

  private implicit lazy val materializer: Materializer = fakeApplication.materializer

  private val CLIENT_ID_HEADER_NAME = "x-client-id"
  private val CONVERSATION_ID_HEADER_NAME = "X-Conversation-ID"

  trait Setup {
    protected val clientId = "abc123"
    protected val uuid: UUID = UUID.randomUUID()

    protected val notification1 = Notification(UUID.randomUUID(), Map.empty, "<xml></xml>", DateTime.now(), None)
    protected val notification2: Notification = notification1.copy(notificationId = UUID.randomUUID())

    class StaticIDGenerator extends NotificationIdGenerator {
      override def generateId(): UUID = uuid
    }

    protected val mockQueueService: QueueService = mock[QueueService]
    protected val mockFieldsService: ApiSubscriptionFieldsService = mock[ApiSubscriptionFieldsService]
    protected val mockCdsLogger: CdsLogger = mock[CdsLogger]
    protected val mockDateTimeProvider: DateTimeProvider = mock[DateTimeProvider]
    protected val controller = new EnhancedNotificationsController(mockQueueService, mockFieldsService, new StaticIDGenerator,
      mockDateTimeProvider, mockCdsLogger)
  }

  "GET /notifications/unread/:id" should {

    "return 200" in new Setup {
      private val payload = "<xml>a</xml>"
      private val inputNotification = Notification(uuid, Map(CONTENT_TYPE -> XML, CONVERSATION_ID_HEADER_NAME -> "5"), payload, DateTime.now(), None)
      private val time = DateTime.now()
      private val outputNotification = inputNotification.copy(dateRead = Some(time)) //TODO MC change now() to actual date

      when(mockDateTimeProvider.now()).thenReturn(time)
      when(mockQueueService.get(clientId, uuid)).thenReturn(Future.successful(Some(inputNotification)))

      val request = FakeRequest(GET, s"/notifications/unread/$uuid", Headers(CLIENT_ID_HEADER_NAME -> clientId), AnyContentAsEmpty)
      val result: Result = await(controller.read(uuid)(request))

      status(result) shouldBe OK
      bodyOf(result) shouldBe payload

      verify(mockQueueService).save(clientId, outputNotification)
      header(CONVERSATION_ID_HEADER_NAME, result) shouldBe Some("5")
      header(CLIENT_ID_HEADER_NAME, result) shouldBe None
    }

    "return 400 if requested notification has already been read" in new Setup {
      private val payload = "<xml>a</xml>"
      private val minutes = 10
      when(mockQueueService.get(clientId, uuid)).thenReturn(Future.successful(Some(Notification(uuid, Map(CONTENT_TYPE -> XML, CONVERSATION_ID_HEADER_NAME -> "5"), payload, DateTime.now(), Some(DateTime.now().minusMinutes(minutes))))))

      val request = FakeRequest(GET, s"/notifications/unread/$uuid", Headers(CLIENT_ID_HEADER_NAME -> clientId), AnyContentAsEmpty)
      val result: Result = await(controller.read(uuid)(request))

      status(result) shouldBe BAD_REQUEST
      bodyOf(result) shouldBe "Notification has been read"
      PassByNameVerifier(mockCdsLogger, "error")
        .withByNameParam("Notification has been read")
        .verify()

    }


    "return 400 when the X-Client-ID header is not present in the request" in new Setup {
      val result: Result = await(controller.read(uuid)(FakeRequest(GET, s"/notifications/unread/$uuid")))

      status(result) shouldBe BAD_REQUEST
      bodyOf(result) shouldBe "X-Client-ID required."
      PassByNameVerifier(mockCdsLogger, "error")
        .withByNameParam("Client id is missing")
        .verify()

    }

    "return 404 if the notification is not found" in new Setup {
      when(mockQueueService.get(clientId, uuid)).thenReturn(Future.successful(None))

      val request = FakeRequest(GET, s"/notifications/unread/$uuid", Headers(CLIENT_ID_HEADER_NAME -> clientId), AnyContentAsEmpty)
      val result: Result = await(controller.read(uuid)(request))

      status(result) shouldBe NOT_FOUND
      bodyOf(result) shouldBe "NOT FOUND"
      PassByNameVerifier(mockCdsLogger, "error")
        .withByNameParam("Notification not found")
        .verify()
    }
  }

}
