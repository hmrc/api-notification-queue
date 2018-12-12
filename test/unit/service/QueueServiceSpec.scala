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

package unit.service

import java.util.UUID

import org.joda.time.DateTime
import org.mockito.Mockito._
import org.scalatest.mockito.MockitoSugar
import uk.gov.hmrc.apinotificationqueue.model.Notification
import uk.gov.hmrc.apinotificationqueue.repository.NotificationRepository
import uk.gov.hmrc.apinotificationqueue.service.QueueService
import uk.gov.hmrc.play.test.UnitSpec

import scala.concurrent.Future

class QueueServiceSpec extends UnitSpec with MockitoSugar {

  trait Setup {
    val mockNotificationRepository: NotificationRepository = mock[NotificationRepository]
    val queueService: QueueService = new QueueService(mockNotificationRepository)

    val clientId: String = "clientId"

    val notification1: Notification = Notification(UUID.randomUUID(), Map.empty, "<xml></xml>", DateTime.now(), None)
    val notification2: Notification = notification1.copy(notificationId = UUID.randomUUID())
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

    "Retrieve all the notifications (by client id) from the mongo repository" in new Setup {
      when(mockNotificationRepository.fetch(clientId)).thenReturn(Future.successful(List(notification1, notification2)))

      await(queueService.get(clientId)) shouldBe List(notification1, notification2)

      verify(mockNotificationRepository).fetch(clientId)
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
