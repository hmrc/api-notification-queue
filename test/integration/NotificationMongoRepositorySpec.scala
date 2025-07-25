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

package integration

import org.mongodb.scala.model.Filters
import org.mongodb.scala.SingleObservableFuture
import org.mongodb.scala.ObservableFuture
import org.scalatest.{BeforeAndAfterAll, BeforeAndAfterEach}
import org.scalatestplus.mockito.MockitoSugar
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.test.Helpers
import uk.gov.hmrc.apinotificationqueue.model.NotificationStatus.{Pulled, Unpulled}
import uk.gov.hmrc.apinotificationqueue.model.{NotificationId, NotificationWithIdOnly}
import uk.gov.hmrc.apinotificationqueue.repository.{ClientNotification, NotificationMongoRepository}
import uk.gov.hmrc.http.InternalServerException
import uk.gov.hmrc.mongo.play.json.formats.MongoUuidFormats
import util.TestData._
import util.UnitSpec

import java.util.UUID
import scala.concurrent.ExecutionContext

class NotificationMongoRepositorySpec extends UnitSpec
  with BeforeAndAfterAll
  with BeforeAndAfterEach
  with MockitoSugar
  with GuiceOneAppPerSuite
  with MongoUuidFormats.Implicits {

  implicit val ec: ExecutionContext = Helpers.stubControllerComponents().executionContext

  lazy val repository: NotificationMongoRepository = app.injector.instanceOf[NotificationMongoRepository]

  override def beforeEach(): Unit = {
    await(repository.collection.drop().toFuture())
    await(repository.ensureIndexes())
  }

  override def afterAll(): Unit = {
    await(repository.collection.drop().toFuture())
  }

  private def collectionSize: Int = {
    await(repository.collection.countDocuments().toFuture().toInt)
  }

  "repository" can {
    "save a single notification" should {
      "be successful" in {

        val saveResult = await(repository.save(ClientId1, Notification1))

        collectionSize shouldBe 1
        saveResult shouldBe Notification1

        findByClientId(ClientId1) shouldBe Seq(Client1Notification1)
      }

      "be successful when called multiple times" in {
        await(repository.save(ClientId1, Notification1))
        await(repository.save(ClientId1, Notification2))
        await(repository.save(ClientId2, Notification3))

        collectionSize shouldBe 3
        val clientNotifications = await(findByClientId(ClientId1))
        clientNotifications.size shouldBe 2

        assert(clientNotifications.contains(Client1Notification1))
        assert(clientNotifications.contains(Client1Notification2))
      }

      "error if duplicated" in {
        await(repository.save(ClientId1, Notification1))
        collectionSize shouldBe 1
        await(repository.save(ClientId1, Notification1))
        collectionSize shouldBe 1
      }
    }

    "update a single notification" should {
      "be successful" in {
        val updatedNotification = Notification1.copy(
          payload = "<foo>THIS HAS BEEN UPDATED</foo>"
        )

        val saveResult = await(repository.save(ClientId1, Notification1))
        collectionSize shouldBe 1
        saveResult shouldBe Notification1

        val updateResult = await(repository.update(ClientId1, updatedNotification))
        collectionSize shouldBe 1

        updateResult shouldBe updatedNotification

        val expectedNotification = ClientNotification(ClientId1, updatedNotification)
        findByClientId(ClientId1) shouldBe Seq(expectedNotification)
      }
    }

    "fetch by clientId and notificationId" should {
      "return a single record when found" in {
        await(repository.save(ClientId1, Notification1))
        await(repository.save(ClientId1, Notification2))

        collectionSize shouldBe 2

        val maybeNotification = await(repository.fetch(ClientId1, Notification1.notificationId))

        maybeNotification shouldBe Some(Notification1)
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

        notificationIdsWithStatus should contain(NotificationWithIdAndPulledStatus1)
        notificationIdsWithStatus should contain(NotificationWithIdAndPulledStatus2)
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

        await(repository.save(ClientId1, Notification1))
        await(repository.save(ClientId1, Notification2))

        collectionSize shouldBe 2

        await(repository.delete(ClientId1, Notification1.notificationId)) shouldBe true

        collectionSize shouldBe 1
        await(repository.fetchNotificationIds(ClientId1, None)).head shouldBe NotificationWithIdOnly(NotificationId(Notification2.notificationId))
      }

      "return false when record not found" in {

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

        excessive shouldBe Symbol("Empty")
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

    "handleError" must {

      "throw runtime error" in {

        val exception = new InternalServerException("there has been an error")

        val logMessage = "an error has occurred"

        intercept[RuntimeException] {

          await(repository.handleError(exception, logMessage))
        }
      }
    }
  }

  /**
   * Queries the collection directly, i.e. not using a method on the [[uk.gov.hmrc.apinotificationqueue.repository.NotificationRepository]]
   * No guarantee given to order of results.
   */
  private def findByClientId(clientId: String): Seq[ClientNotification] = await(repository.collection.find(Filters.equal("clientId", clientId)).toFuture())
}
