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

package unit.service

import org.mockito.Mockito._
import org.scalatestplus.mockito.MockitoSugar
import play.api.test.Helpers
import uk.gov.hmrc.apinotificationqueue.model.NotificationStatus._
import uk.gov.hmrc.apinotificationqueue.model.{Notification, NotificationId, NotificationWithIdAndPulled, NotificationWithIdOnly}
import uk.gov.hmrc.apinotificationqueue.repository.NotificationRepository
import uk.gov.hmrc.apinotificationqueue.service.QueueService
import util.TestData.{ConversationId1Uuid, Notification1}
import util.UnitSpec

import java.time.Instant
import java.util.UUID
import scala.concurrent.{ExecutionContext, Future}

class QueueServiceSpec extends UnitSpec with MockitoSugar {

  trait Setup {
    implicit val ec: ExecutionContext = Helpers.stubControllerComponents().executionContext
    val mockNotificationRepository: NotificationRepository = mock[NotificationRepository]
    val queueService: QueueService = new QueueService(mockNotificationRepository)

    val clientId: String = "clientId"

    val notification1: Notification = Notification(UUID.randomUUID(), ConversationId1Uuid, Map.empty, "<xml></xml>", Instant.now(), None)
    val notification2: Notification = notification1.copy(notificationId = UUID.randomUUID(), datePulled = Some(Instant.now()))

    val notificationWithIdOnly1 = NotificationWithIdOnly(NotificationId(notification1.notificationId))
    val notificationWithIdOnly2 = NotificationWithIdOnly(NotificationId(notification2.notificationId))
  }

  "QueueService" should {

    "Save the notification in the mongo repository" in new Setup {
      when(mockNotificationRepository.save(clientId, notification1)).thenReturn(Future.successful(notification1))

      await(queueService.save(clientId, notification1)) shouldBe notification1

      verify(mockNotificationRepository).save(clientId, notification1)
    }

    "Update notification in the mongo repository" in new Setup {
      when(mockNotificationRepository.update(clientId, notification1)).thenReturn(Future.successful(notification1))

      await(queueService.update(clientId, notification1)) shouldBe notification1

      verify(mockNotificationRepository).update(clientId, notification1)
    }

    "Retrieve all the notificationIds (by client id) from the mongo repository" in new Setup {
      when(mockNotificationRepository.fetchNotificationIds(clientId, None)).thenReturn(Future.successful(List(notificationWithIdOnly1, notificationWithIdOnly2)))

      await(queueService.get(clientId, None)) shouldBe List(notificationWithIdOnly1, notificationWithIdOnly2)

      verify(mockNotificationRepository).fetchNotificationIds(clientId, None)
    }

    "Retrieve all the pulled notificationIds by client id from the mongo repository" in new Setup {
      when(mockNotificationRepository.fetchNotificationIds(clientId, Some(Pulled))).thenReturn(Future.successful(List(notificationWithIdOnly2)))

      await(queueService.get(clientId, Some(Pulled))) shouldBe List(notificationWithIdOnly2)

      verify(mockNotificationRepository).fetchNotificationIds(clientId, Some(Pulled))
    }

    "Retrieve all notificationIds by client id and conversation id from the mongo repository" in new Setup {
      when(mockNotificationRepository.fetchNotificationIds(clientId, ConversationId1Uuid))
        .thenReturn(Future.successful(List(NotificationWithIdAndPulled(NotificationId(Notification1.notificationId), pulled = true))))

      await(queueService.getByConversationId(clientId, ConversationId1Uuid)) shouldBe List(NotificationWithIdAndPulled(NotificationId(Notification1.notificationId), pulled = true))

      verify(mockNotificationRepository).fetchNotificationIds(clientId, ConversationId1Uuid)
    }

    "Retrieve all notificationIds by client id, conversation id and status from the mongo repository" in new Setup {
      when(mockNotificationRepository.fetchNotificationIds(clientId, ConversationId1Uuid, Pulled))
        .thenReturn(Future.successful(List(NotificationWithIdOnly(NotificationId(Notification1.notificationId)))))

      await(queueService.getByConversationId(clientId, ConversationId1Uuid, Pulled)) shouldBe List(NotificationWithIdOnly(NotificationId(Notification1.notificationId)))

      verify(mockNotificationRepository).fetchNotificationIds(clientId, ConversationId1Uuid, Pulled)
    }

    "Retrieve the expected notification from the mongo repository" in new Setup {
      when(mockNotificationRepository.fetch(clientId, notification1.notificationId)).thenReturn(Future.successful(Some(notification1)))

      await(queueService.get(clientId, notification1.notificationId)) shouldBe Some(notification1)

      verify(mockNotificationRepository).fetch(clientId, notification1.notificationId)
    }

    "Delete the expected notification from the mongo repository" in new Setup {
      when(mockNotificationRepository.delete(clientId, notification1.notificationId)).thenReturn(Future.successful(true))

      await(queueService.delete(clientId, notification1.notificationId)) shouldBe true

      verify(mockNotificationRepository).delete(clientId, notification1.notificationId)
    }

  }

}
