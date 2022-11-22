/*
 * Copyright 2022 HM Revenue & Customs
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

import com.google.inject.ImplementedBy
import play.api.libs.json.Json
import reactivemongo.api.Cursor
import reactivemongo.api.indexes.{Index, IndexType}
import reactivemongo.bson.{BSONDocument, BSONLong, BSONNull, BSONObjectID}
import reactivemongo.play.json._
import uk.gov.hmrc.apinotificationqueue.model.NotificationStatus._
import uk.gov.hmrc.apinotificationqueue.model._
import uk.gov.hmrc.apinotificationqueue.repository.ClientNotification.ClientNotificationJF
import uk.gov.hmrc.customs.api.common.logging.CdsLogger
import uk.gov.hmrc.mongo.ReactiveRepository
import uk.gov.hmrc.mongo.json.ReactiveMongoFormats

import java.util.UUID
import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}
import scala.util.control.NonFatal

@ImplementedBy(classOf[NotificationMongoRepository])
trait NotificationRepository {
  def save(clientId: String, notification: Notification): Future[Notification]

  def update(clientId: String, notification: Notification): Future[Notification]

  def fetch(clientId: String, notificationId: UUID): Future[Option[Notification]]

  def fetchNotificationIds(clientId: String, notificationStatus: Option[NotificationStatus.Value]): Future[List[NotificationWithIdOnly]]

  def fetchNotificationIds(clientId: String, conversationId: UUID, notificationStatus: NotificationStatus.Value): Future[List[NotificationWithIdOnly]]

  def fetchNotificationIds(clientId: String, conversationId: UUID): Future[List[NotificationWithIdAndPulled]]

  def fetchOverThreshold(threshold: Int): Future[List[ClientOverThreshold]]

  def delete(clientId: String, notificationId: UUID): Future[Boolean]

  def deleteAll(): Future[Unit]
}

@Singleton
class NotificationMongoRepository @Inject()(mongoDbProvider: MongoDbProvider,
                                            notificationRepositoryErrorHandler: NotificationRepositoryErrorHandler,
                                            cdsLogger: CdsLogger,
                                            config: ApiNotificationQueueConfig)(implicit ec: ExecutionContext)
  extends ReactiveRepository[ClientNotification, BSONObjectID]("notifications", mongoDbProvider.mongo,
    ClientNotification.ClientNotificationJF, ReactiveMongoFormats.objectIdFormats)
    with NotificationRepository {

  private val ttlIndexName = "dateReceived-Index"
  private val ttlInSeconds = config.ttlInSeconds
  private val ttlIndex = Index(
    key = Seq("notification.dateReceived" -> IndexType.Descending),
    name = Some(ttlIndexName),
    unique = false,
    options = BSONDocument("expireAfterSeconds" -> BSONLong(ttlInSeconds))
  )

  dropInvalidIndexes.flatMap { _ =>
    collection.indexesManager.ensure(ttlIndex)
  }

  override def indexes: Seq[Index] = {
    Seq(
      Index(
        key = Seq("clientId" -> IndexType.Ascending),
        name = Some("clientId-Index"),
        unique = false
      ),
      Index(
        key = Seq("clientId" -> IndexType.Ascending, "notification.notificationId" -> IndexType.Ascending),
        name = Some("clientId-notificationId-Index"),
        unique = true
      ),
      Index(
        key = Seq("clientId" -> IndexType.Ascending, "notification.datePulled" -> IndexType.Ascending),
        name = Some("clientId-datePulled-Index"),
        unique = false
      ),
      Index(
        key = Seq("clientId" -> IndexType.Ascending, "notification.conversationId" -> IndexType.Ascending),
        name = Some("clientId-conversationId-Index"),
        unique = false
      ),
      Index(
        key = Seq("clientId" -> IndexType.Ascending, "notification.conversationId" -> IndexType.Ascending, "notification.datePulled" -> IndexType.Ascending),
        name = Some("clientId-conversationId-datePulled-Index"),
        unique = false
      ),
      ttlIndex
    )
  }

  override def save(clientId: String, notification: Notification): Future[Notification] = {
    cdsLogger.debug(s"saving clientId: [$clientId] from notification: [$notification]")

    val clientNotification = ClientNotification(clientId, notification)

    lazy val errorMsg = s"Notification not saved for client [$clientId]"

    insert(clientNotification).map {
      writeResult => notificationRepositoryErrorHandler.handleSaveError(writeResult, errorMsg, notification)
    }.recover {
      case NonFatal(e) => logger.error(errorMsg, e)
        notification
    }

  }

  override def update(clientId: String, notification: Notification): Future[Notification] = {
    cdsLogger.debug(s"updating clientId: $clientId, notificationId: ${notification.notificationId}")

    val clientNotification = ClientNotification(clientId, notification)

    lazy val errorMsg = s"Notification not updated for clientId $clientId, notificationId: ${notification.notificationId}"

    val selector = Json.obj("clientId" -> clientId, "notification.notificationId" -> notification.notificationId)

    findAndUpdate(selector, ClientNotificationJF.writes(clientNotification)).map {
      result =>
        notificationRepositoryErrorHandler.handleUpdateError(result, errorMsg, notification)
    }.recoverWith {
      case error =>
        Future.failed(error)
    }
  }

  override def fetch(clientId: String, notificationId: UUID): Future[Option[Notification]] = {
    find("clientId" -> clientId, "notification.notificationId" -> notificationId).map {
      _.headOption.map(cn => cn.notification)
    }
  }

  override def fetchOverThreshold(threshold: Int): Future[List[ClientOverThreshold]] = {
    import collection.BatchCommands.AggregationFramework.{Group, Match, MaxField, MinField, Project, SumAll}

    collection.aggregatorContext[ClientOverThreshold](
      Group(Json.obj("clientId" -> "$clientId"))("notificationTotal" -> SumAll,
        "oldestNotification" -> MinField("notification.dateReceived"),
        "latestNotification" -> MaxField("notification.dateReceived")
      ),
      List(Match(Json.obj("notificationTotal" -> Json.obj("$gte" -> threshold))),
        Project(Json.obj("_id" -> 0,
          "clientId" -> "$_id.clientId",
          "notificationTotal" -> "$notificationTotal",
          "oldestNotification" -> "$oldestNotification",
          "latestNotification" -> "$latestNotification"
        ))))
      .prepared
      .cursor
      .collect[List](-1, reactivemongo.api.Cursor.FailOnError[List[ClientOverThreshold]]())
  }

  override def delete(clientId: String, notificationId: UUID): Future[Boolean] = {
    val selector = Json.obj("clientId" -> clientId, "notification.notificationId" -> notificationId)
    lazy val errorMsg = s"Could not delete entity for selector: $selector"
    remove("clientId" -> clientId, "notification.notificationId" -> notificationId).map(notificationRepositoryErrorHandler.handleDeleteError(_, errorMsg))
  }

  override def fetchNotificationIds(clientId: String, notificationStatus: Option[NotificationStatus.Value]): Future[List[NotificationWithIdOnly]] = {
    val selector = notificationStatus match {
      case Some(Pulled) => Json.obj("clientId" -> clientId, "notification.datePulled" -> Json.obj("$exists" -> true))
      case Some(Unpulled) => Json.obj("clientId" -> clientId, "notification.datePulled" -> Json.obj("$exists" -> false))
      case _ => Json.obj("clientId" -> clientId)
    }
    val projection = Json.obj("notification.notificationId" -> 1, "_id" -> 0)

    collection.find(selector, Some(projection)).cursor[NotificationWithIdOnly]().collect[List](Int.MaxValue, Cursor.FailOnError[List[NotificationWithIdOnly]]())
  }

  override def fetchNotificationIds(clientId: String, conversationId: UUID, notificationStatus: NotificationStatus.Value): Future[List[NotificationWithIdOnly]] = {

    val selector = notificationStatus match {
      case Pulled => Json.obj("clientId" -> clientId, "notification.conversationId" -> conversationId, "notification.datePulled" -> Json.obj("$exists" -> true))
      case Unpulled => Json.obj("clientId" -> clientId, "notification.conversationId" -> conversationId, "notification.datePulled" -> Json.obj("$exists" -> false))
    }
    val projection = Json.obj("notification.notificationId" -> 1, "_id" -> 0)

    collection.find(selector, Some(projection)).cursor[NotificationWithIdOnly]().collect[List](Int.MaxValue, Cursor.FailOnError[List[NotificationWithIdOnly]]())
  }

  override def fetchNotificationIds(clientId: String, conversationId: UUID): Future[List[NotificationWithIdAndPulled]] = {
    import collection.BatchCommands.AggregationFramework.{Match, Project}

    collection.aggregatorContext[NotificationWithIdAndPulled](
      Match(Json.obj("clientId" -> clientId, "notification.conversationId" -> conversationId)),
      List(Project(Json.obj("_id" -> 0,
        "notification" -> 1,
        "pulled" -> Json.obj("$gt" -> Json.arr("$notification.datePulled", BSONNull))
      ))))
      .prepared
      .cursor
      .collect[List](-1, reactivemongo.api.Cursor.FailOnError[List[NotificationWithIdAndPulled]]())
  }

  override def deleteAll(): Future[Unit] = {
    cdsLogger.debug(s"deleting all notifications")

    removeAll().map { result =>
      cdsLogger.debug(s"deleted ${result.n} notifications")
    }
  }

  private def dropInvalidIndexes: Future[_] =
    collection.indexesManager.list().flatMap { indexes =>
      indexes
        .find { index =>
          index.name.contains(ttlIndexName) &&
            !index.options.getAs[Int]("expireAfterSeconds").contains(ttlInSeconds)
        }
        .map { _ =>
          logger.debug(s"dropping $ttlIndexName index as ttl value is incorrect")
          collection.indexesManager.drop(ttlIndexName)
        }
        .getOrElse(Future.successful(()))
    }

}
