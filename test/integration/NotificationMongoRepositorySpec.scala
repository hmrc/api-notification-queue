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

package integration

import org.joda.time.{DateTime, DateTimeZone}
import org.mockito.ArgumentMatchers._
import org.mockito.Mockito._
import org.scalatest.mockito.MockitoSugar
import org.scalatest.{BeforeAndAfterAll, BeforeAndAfterEach}
import play.api.libs.json.Json
import reactivemongo.api.{Cursor, DB}
import reactivemongo.play.json._
import uk.gov.hmrc.apinotificationqueue.model.{ApiNotificationQueueConfig, Notification}
import uk.gov.hmrc.apinotificationqueue.repository.ClientNotification.ClientNotificationJF
import uk.gov.hmrc.apinotificationqueue.repository.{ClientNotification, MongoDbProvider, NotificationMongoRepository, NotificationRepositoryErrorHandler}
import uk.gov.hmrc.customs.api.common.logging.CdsLogger
import uk.gov.hmrc.mongo.MongoSpecSupport
import uk.gov.hmrc.play.test.UnitSpec
import util.TestData._

import scala.concurrent.ExecutionContext.Implicits.global

class NotificationMongoRepositorySpec extends UnitSpec
  with BeforeAndAfterAll
  with BeforeAndAfterEach
  with MockitoSugar
  with MongoSpecSupport  { self =>

  private val mongoDbProvider: MongoDbProvider = new MongoDbProvider {
    override val mongo: () => DB = self.mongo
  }

  private val mockCdsLogger = mock[CdsLogger]
  private val mockErrorHandler = mock[NotificationRepositoryErrorHandler]
  private val mockConfigService = mock[ApiNotificationQueueConfig]
  private val repository = new NotificationMongoRepository(mongoDbProvider, mockErrorHandler, mockCdsLogger, mockConfigService)

  override def beforeEach() {
    super.beforeEach()
    await(repository.drop)
  }

  override def afterAll() {
    super.afterAll()
    await(repository.drop)
  }

  private def collectionSize: Int = {
    await(repository.collection.count())
  }

  private def selector(clientId: String) = {
    Json.obj("clientId" -> clientId)
  }

  "repository" can {
    "save a single notification" should {
      "be successful" in {
        when(mockErrorHandler.handleSaveError(any(), any(), any())).thenReturn(Notification1)

        val actualMessage = await(repository.save(ClientId1, Notification1))

        collectionSize shouldBe 1
        actualMessage shouldBe Notification1
        val actualNotification = await(repository.collection.find(selector(ClientId1)).one[ClientNotification]).get
        actualNotification shouldBe Client1Notification1
      }

      "be successful when called multiple times" in {
        await(repository.save(ClientId1, Notification1))
        await(repository.save(ClientId1, Notification2))
        await(repository.save(ClientId2, Notification3))

        collectionSize shouldBe 3
        val clientNotifications = await(repository.collection.find(selector(ClientId1)).cursor[ClientNotification]().collect[List](Int.MaxValue, Cursor.FailOnError[List[ClientNotification]]()))
        clientNotifications.size shouldBe 2
        clientNotifications should contain(Client1Notification1)
        clientNotifications should contain(Client1Notification2)
      }
    }

    "update a single notification" should {
      "be successful" in {
        val time = DateTime.now(DateTimeZone.UTC)
        val updatedNotification = Notification1.copy(datePulled = Some(time))

        when(mockErrorHandler.handleSaveError(any(), any(), any())).thenReturn(Notification1)

        val actualMessage1 = await(repository.save(ClientId1, Notification1))
        collectionSize shouldBe 1
        actualMessage1 shouldBe Notification1

        when(mockErrorHandler.handleSaveError(any(), any(), any())).thenReturn(updatedNotification)

        val actualMessage2 = await(repository.update(ClientId1, updatedNotification))
        collectionSize shouldBe 1
        actualMessage2 shouldBe updatedNotification

        val actualNotification = await(repository.collection.find(selector(ClientId1)).one[ClientNotification]).get
        val expectedNotification = ClientNotification(ClientId1, updatedNotification)

        actualNotification shouldBe expectedNotification
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

    "fetch by clientId" should {
      "return all notifications when found by clientId" in {
        await(repository.save(ClientId1, Notification1))
        await(repository.save(ClientId1, Notification2))
        await(repository.save(ClientId2, Notification3))

        val notifications: List[Notification] = await(repository.fetch(ClientId1))

        notifications.size shouldBe 2
        notifications should contain(Notification1)
        notifications should contain(Notification2)
      }

      "return None when not found" in {
        await(repository.save(ClientId1, Notification1))
        await(repository.save(ClientId1, Notification2))

        await(repository.fetch("DOES_NOT_EXIST_CLIENT_ID")) shouldBe Nil
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
        await(repository.fetch(ClientId1)).head shouldBe Notification2
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
  }

}
