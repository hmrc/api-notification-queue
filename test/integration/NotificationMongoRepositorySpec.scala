/*
 * Copyright 2021 HM Revenue & Customs
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

package integration

import java.util.UUID

import org.joda.time.{DateTime, DateTimeZone}
import org.mockito.ArgumentMatchers._
import org.mockito.Mockito._
import org.scalatest.{BeforeAndAfterAll, BeforeAndAfterEach}
import org.scalatestplus.mockito.MockitoSugar
import play.api.libs.json.Json
import play.api.test.Helpers
import reactivemongo.api.DB
import uk.gov.hmrc.apinotificationqueue.model.NotificationStatus._
import uk.gov.hmrc.apinotificationqueue.model.{ApiNotificationQueueConfig, NotificationId, NotificationWithIdOnly}
import uk.gov.hmrc.apinotificationqueue.repository.{ClientNotification, MongoDbProvider, NotificationMongoRepository, NotificationRepositoryErrorHandler}
import uk.gov.hmrc.mongo.MongoSpecSupport
import uk.gov.hmrc.play.bootstrap.config.ServicesConfig
import util.UnitSpec
import util.StubCdsLogger
import util.TestData._

class NotificationMongoRepositorySpec extends UnitSpec
  with BeforeAndAfterAll
  with BeforeAndAfterEach
  with MockitoSugar
  with MongoSpecSupport  { self =>

  private val mongoDbProvider: MongoDbProvider = new MongoDbProvider {
    override val mongo: () => DB = self.mongo
  }

  implicit val ec = Helpers.stubControllerComponents().executionContext
  private val cdsLogger = new StubCdsLogger(mock[ServicesConfig])
  private val mockErrorHandler = mock[NotificationRepositoryErrorHandler]
  private val mockConfigService = mock[ApiNotificationQueueConfig]
  private val repository = new NotificationMongoRepository(mongoDbProvider, mockErrorHandler, cdsLogger, mockConfigService)

  override def beforeEach() {
    dropTestCollection("notifications")
  }

  override def afterAll() {
    dropTestCollection("notifications")
  }

  private def collectionSize: Int = {
    await(repository.count(Json.obj()))
  }

  "repository" can {
    "save a single notification" should {
      "be successful" in {
        when(mockErrorHandler.handleSaveError(any(), any(), any())).thenReturn(Notification1)

        val actualMessage = await(repository.save(ClientId1, Notification1))

        collectionSize shouldBe 1
        actualMessage shouldBe Notification1
        fetchNotification shouldBe Client1Notification1
      }

      "be successful when called multiple times" in {
        await(repository.save(ClientId1, Notification1))
        await(repository.save(ClientId1, Notification2))
        await(repository.save(ClientId2, Notification3))

        collectionSize shouldBe 3
        val clientNotifications = await(repository.find("clientId" -> ClientId1))
        clientNotifications.size shouldBe 2
        clientNotifications should contain(Client1Notification1)
        clientNotifications should contain(Client1Notification2)
      }
    }

    "update a single notification" should {
      "be successful" in {
        val time = DateTime.now(DateTimeZone.UTC)
        val updatedNotification = Notification1.copy(datePulled = Some(time))

        when(mockErrorHandler.handleUpdateError(any(), any(), any())).thenReturn(Notification1)
        val actualMessage1 = await(repository.save(ClientId1, Notification1))
        collectionSize shouldBe 1
        actualMessage1 shouldBe Notification1

        when(mockErrorHandler.handleUpdateError(any(), any(), any())).thenReturn(updatedNotification)
        val actualMessage2 = await(repository.update(ClientId1, updatedNotification))
        collectionSize shouldBe 1
        actualMessage2 shouldBe updatedNotification

        val expectedNotification = ClientNotification(ClientId1, updatedNotification)
        fetchNotification shouldBe expectedNotification
      }
    }

    "fetch by clientId and notificationId" should {
      "return a single record when found" in {
        await(repository.save(ClientId1, Notification1))
        await(repository.save(ClientId1, Notification2))

        val maybeNotification = await(repository.fetch(ClientId1, Notification1.notificationId))

        maybeNotification.get shouldBe Notification1
      }

      "return None when not found" in {
        await(repository.save(ClientId1, Notification1))
        await(repository.save(ClientId1, Notification2))
        val nonExistentNotificationId = Notification3.notificationId

        val maybeNotification = await(repository.fetch(ClientId1, nonExistentNotificationId))

        maybeNotification shouldBe None
      }
    }

    "fetch by clientId and conversationId" should {
      "return all notificationIds and statuses when found" in {
        await(repository.save(ClientId1, Notification1))
        await(repository.save(ClientId2, Notification3))
        await(repository.save(ClientId1, Notification2))

        val notificationIdsWithStatus = await(repository.fetchNotificationIds(ClientId1, ConversationId1Uuid))

        notificationIdsWithStatus shouldBe NotificationWithIdAndPulledStatus1 :: NotificationWithIdAndPulledStatus2 :: Nil
      }

      "return empty list when nothing found" in {
        await(repository.save(ClientId1, Notification1))
        val nonExistentConversationId = UUID.randomUUID()

        val notificationIdsWithStatus = await(repository.fetchNotificationIds(ClientId1, nonExistentConversationId))

        notificationIdsWithStatus shouldBe Nil
      }
    }

    "fetch by clientId, conversationId and status" should {
      "return all notificationIds when found" in {
        await(repository.save(ClientId1, Notification1))
        await(repository.save(ClientId2, Notification3))
        await(repository.save(ClientId1, Notification2))

        val notifications = await(repository.fetchNotificationIds(ClientId1, ConversationId1Uuid, Unpulled))

        notifications.size shouldBe 1
        notifications should contain(NotificationWithIdOnly(NotificationId(Notification1.notificationId)))
      }

      "return all notificationIds for unpulled status when found" in {
        await(repository.save(ClientId1, Notification1))
        await(repository.save(ClientId1, Notification2))
        await(repository.save(ClientId2, Notification2))
        await(repository.save(ClientId1, Notification3))

        val notifications = await(repository.fetchNotificationIds(ClientId1, ConversationId1Uuid, Unpulled))

        notifications.size shouldBe 2
        notifications should contain(NotificationWithIdOnly(NotificationId(Notification1.notificationId)))
        notifications should contain(NotificationWithIdOnly(NotificationId(Notification3.notificationId)))
      }

      "return empty list when nothing found" in {
        await(repository.save(ClientId1, Notification1))
        val nonExistentConversationId = UUID.randomUUID()

        await(repository.fetchNotificationIds(ClientId1, nonExistentConversationId, Pulled)) shouldBe Nil
      }
    }

    "fetch by clientId" should {
      "return all notificationIds when found by clientId" in {
        await(repository.save(ClientId1, Notification1))
        await(repository.save(ClientId1, Notification2))
        await(repository.save(ClientId2, Notification3))

        val notifications: List[NotificationWithIdOnly] = await(repository.fetchNotificationIds(ClientId1, None))

        notifications.size shouldBe 2
        notifications should contain(NotificationWithIdOnly(NotificationId(Notification1.notificationId)))
        notifications should contain(NotificationWithIdOnly(NotificationId(Notification2.notificationId)))
      }

      "return all pulled notificationIds when found by clientId" in {
        await(repository.save(ClientId1, Notification1))
        await(repository.save(ClientId1, Notification2))
        await(repository.save(ClientId2, Notification3))

        val notifications = await(repository.fetchNotificationIds(ClientId1, Some(Pulled)))

        notifications.size shouldBe 1
        notifications should contain(NotificationWithIdOnly(NotificationId(Notification2.notificationId)))
      }

      "return all unpulled notificationIds when found by clientId" in {
        await(repository.save(ClientId1, Notification1))
        await(repository.save(ClientId1, Notification2))
        await(repository.save(ClientId2, Notification3))

        val notifications: List[NotificationWithIdOnly] = await(repository.fetchNotificationIds(ClientId1, Some(Unpulled)))

        notifications.size shouldBe 1
        notifications should contain(NotificationWithIdOnly(NotificationId(Notification1.notificationId)))
      }

      "return empty List when not found" in {
        await(repository.save(ClientId1, Notification1))
        await(repository.save(ClientId1, Notification2))

        await(repository.fetchNotificationIds("DOES_NOT_EXIST_CLIENT_ID", None)) shouldBe Nil
      }
    }

    "delete by clientId and notificationId" should {
      "return true when record found and deleted" in {
        when(mockErrorHandler.handleDeleteError(any(), any())).thenReturn(true)

        await(repository.save(ClientId1, Notification1))
        await(repository.save(ClientId1, Notification2))

        collectionSize shouldBe 2

        await(repository.delete(ClientId1, Notification1.notificationId)) shouldBe true

        collectionSize shouldBe 1
        await(repository.fetchNotificationIds(ClientId1, None)).head shouldBe NotificationWithIdOnly(NotificationId(Notification2.notificationId))
      }

      "return false when record not found" in {
        when(mockErrorHandler.handleDeleteError(any(), any())).thenReturn(false)

        await(repository.save(ClientId1, Notification1))
        await(repository.save(ClientId1, Notification2))
        collectionSize shouldBe 2

        await(repository.delete("DOES_NOT_EXIST_CLIENT_ID", Notification1.notificationId)) shouldBe false

        collectionSize shouldBe 2
      }
    }

    "fetch over threshold" should {
      "fetch only those notifications whose total by clientId is over threshold" in {
        await(repository.save(ClientId1, Notification1))
        await(repository.save(ClientId1, Notification2))
        await(repository.save(ClientId2, Notification1))

        val excessive = await(repository.fetchOverThreshold(2))

        excessive.size shouldBe 1
        excessive should contain(ClientOverThreshold1)
        excessive.head.latestNotification.isAfter(excessive.head.oldestNotification) shouldBe true
      }

      "return no clients when notifications don't breach threshold" in {
        await(repository.save(ClientId1, Notification1))
        await(repository.save(ClientId1, Notification2))
        await(repository.save(ClientId2, Notification1))

        val excessive = await(repository.fetchOverThreshold(3))

        excessive shouldBe 'Empty
      }
    }
    
    "delete all" should {
      "successfully delete all notifications" in {
        await(repository.save(ClientId1, Notification1))
        await(repository.save(ClientId2, Notification1))
        collectionSize shouldBe 2

        await(repository.deleteAll())

        collectionSize shouldBe 0
      }

    }
    
  }

  private def fetchNotification: ClientNotification = await(repository.find("clientId" -> ClientId1).head)
}
