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

package uk.gov.hmrc.apinotificationqueue.repository

import org.scalatest.mockito.MockitoSugar
import org.scalatest.{BeforeAndAfterAll, BeforeAndAfterEach}
import play.api.libs.json.Json
import reactivemongo.api.{Cursor, DB}
import reactivemongo.play.json._
import uk.gov.hmrc.apinotificationqueue.model.Notification
import uk.gov.hmrc.apinotificationqueue.repository.ClientNotification.ClientNotificationJF
import uk.gov.hmrc.mongo.MongoSpecSupport
import uk.gov.hmrc.play.test.UnitSpec
import uk.gov.hmrc.apinotificationqueue.TestData._

import scala.concurrent.ExecutionContext.Implicits.global

class NotificationMongoRepositorySpec extends UnitSpec
  with BeforeAndAfterAll
  with BeforeAndAfterEach
  with MockitoSugar
  with MongoSpecSupport  { self =>

  private val mongoDbProvider = new MongoDbProvider {
    override val mongo: () => DB = self.mongo
  }

  private val repository = new NotificationMongoRepository(mongoDbProvider)

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
        val actualMessage = await(repository.save(clientId1, notification1))

        collectionSize shouldBe 1
        actualMessage shouldBe notification1
        await(repository.collection.find(selector(clientId1)).one[ClientNotification]).get shouldBe client1Notification1
      }

      "be successful when called multiple times" in {
        await(repository.save(clientId1, notification1))
        await(repository.save(clientId1, notification2))
        await(repository.save(clientId2, notification3))

        collectionSize shouldBe 3
        val clientNotifications = await(repository.collection.find(selector(clientId1)).cursor[ClientNotification]().collect[List](Int.MaxValue, Cursor.FailOnError[List[ClientNotification]]()))
        clientNotifications.size shouldBe 2
        clientNotifications should contain(client1Notification1)
        clientNotifications should contain(client1Notification2)
      }
    }

    "fetch by clientId and notificationId" should {
      "return a single record when found" in {
        await(repository.save(clientId1, notification1))
        await(repository.save(clientId1, notification2))

        val maybeNotification = await(repository.fetch(clientId1, notification1.notificationId))

        maybeNotification.get shouldBe notification1
      }

      "return None when not found" in {
        await(repository.save(clientId1, notification1))
        await(repository.save(clientId1, notification2))
        val nonExistentNotificationId = notification3.notificationId

        val maybeNotification = await(repository.fetch(clientId1, nonExistentNotificationId))

        maybeNotification shouldBe None
      }
    }

    "fetch by clientId" should {
      "return all notifications when found by clientId" in {
        await(repository.save(clientId1, notification1))
        await(repository.save(clientId1, notification2))
        await(repository.save(clientId2, notification3))

        val notifications: List[Notification] = await(repository.fetch(clientId1))

        notifications.size shouldBe 2
        notifications should contain(notification1)
        notifications should contain(notification2)
      }

      "return None when not found" in {
        await(repository.save(clientId1, notification1))
        await(repository.save(clientId1, notification2))

        await(repository.fetch("DOES_NOT_EXIST_CLIENT_ID")) shouldBe Nil
      }
    }

    "delete by clientId and notificationId" should {
      "return true when record found and deleted" in {
        await(repository.save(clientId1, notification1))
        await(repository.save(clientId1, notification2))

        collectionSize shouldBe 2

        await(repository.delete(clientId1, notification1.notificationId)) shouldBe true

        collectionSize shouldBe 1
        await(repository.fetch(clientId1)).head shouldBe notification2
      }

      "return false when record not found" in {
        await(repository.save(clientId1, notification1))
        await(repository.save(clientId1, notification2))
        collectionSize shouldBe 2

        await(repository.delete("DOES_NOT_EXIST_CLIENT_ID", notification1.notificationId)) shouldBe false

        collectionSize shouldBe 2
      }
    }

    "fetch over threshold" should {
      "fetch only those notifications whose total by clientId is over threshold" in {
        await(repository.save(clientId1, notification1))
        await(repository.save(clientId1, notification2))
        await(repository.save(clientId2, notification1))

        val excessive = await(repository.fetchOverThreshold(2))

        excessive.size shouldBe 1
        excessive should contain(clientOverThreshold1)
        excessive.head.latestNotification.isAfter(excessive.head.oldestNotification) shouldBe true
      }

      "return no clients when notifications don't breach threshold" in {
        await(repository.save(clientId1, notification1))
        await(repository.save(clientId1, notification2))
        await(repository.save(clientId2, notification1))

        val excessive = await(repository.fetchOverThreshold(3))

        excessive shouldBe 'Empty
      }
    }
  }

}
