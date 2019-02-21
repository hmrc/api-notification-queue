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

package uk.gov.hmrc.apinotificationqueue.repository

import java.util.UUID

import com.google.inject.ImplementedBy
import javax.inject.{Inject, Singleton}
import play.api.libs.json.{Format, Json}
import reactivemongo.api.Cursor
import reactivemongo.api.indexes.{Index, IndexType}
import reactivemongo.bson.{BSONDocument, BSONLong, BSONObjectID}
import reactivemongo.play.json._
import uk.gov.hmrc.apinotificationqueue.model.NotificationStatus._
import uk.gov.hmrc.apinotificationqueue.model.{ApiNotificationQueueConfig, Notification, NotificationStatus, NotificationWithIdOnly}
import uk.gov.hmrc.apinotificationqueue.repository.ClientNotification.ClientNotificationJF
import uk.gov.hmrc.customs.api.common.logging.CdsLogger
import uk.gov.hmrc.mongo.ReactiveRepository
import uk.gov.hmrc.mongo.json.ReactiveMongoFormats

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

@ImplementedBy(classOf[NotificationMongoRepository])
trait NotificationRepository {
  def save(clientId: String, notification: Notification): Future[Notification]

  def update(clientId: String, notification: Notification): Future[Notification]

  def fetch(clientId: String, notificationId: UUID): Future[Option[Notification]]

  def fetchNotificationIds(clientId: String, notificationStatus: Option[NotificationStatus.Value]): Future[List[NotificationWithIdOnly]]

  def fetchOverThreshold(threshold: Int): Future[List[ClientOverThreshold]]

  def delete(clientId: String, notificationId: UUID): Future[Boolean]
}

@Singleton
class NotificationMongoRepository @Inject()(mongoDbProvider: MongoDbProvider,
                                            notificationRepositoryErrorHandler: NotificationRepositoryErrorHandler,
                                            cdsLogger: CdsLogger,
                                            config: ApiNotificationQueueConfig)
  extends ReactiveRepository[ClientNotification, BSONObjectID]("notifications", mongoDbProvider.mongo,
    ClientNotification.ClientNotificationJF, ReactiveMongoFormats.objectIdFormats)
    with NotificationRepository {

  private implicit val format: Format[ClientNotification] = ClientNotificationJF

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
        key = Seq("notification.dateReceived" -> IndexType.Descending),
        name = Some("dateReceived-Index"),
        unique = false,
        options = BSONDocument("expireAfterSeconds" -> BSONLong(config.ttlInSeconds))
      )

    )
  }

  override def save(clientId: String, notification: Notification): Future[Notification] = {
    cdsLogger.debug(s"saving clientId: $clientId")

    val clientNotification = ClientNotification(clientId, notification)

    lazy val errorMsg = s"Notification not saved for client $clientId"

    insert(clientNotification).map {
      writeResult => notificationRepositoryErrorHandler.handleSaveError(writeResult, errorMsg, notification)
    }
  }

  override def update(clientId: String, notification: Notification): Future[Notification] = {
    cdsLogger.debug(s"updating clientId: $clientId, notificationId: ${notification.notificationId}")

    val clientNotification = ClientNotification(clientId, notification)

    lazy val errorMsg = s"Notification not updated for clientId $clientId, notificationId: ${notification.notificationId}"

    val selector = Json.obj("clientId" -> clientId, "notification.notificationId" -> notification.notificationId)

    findAndUpdate(selector, ClientNotificationJF.writes(clientNotification)).map {
      res =>
        res.lastError.fold(notification){err =>
          if (err.n > 0) {
            notification
          } else {
            throw new RuntimeException("clientId not found")
          }
        }
    }.recover {
      case error =>
        val msg = s"$errorMsg. ${error.getMessage}"
        cdsLogger.error(msg)
        throw new RuntimeException(msg)
    }
  }

  override def fetch(clientId: String, notificationId: UUID): Future[Option[Notification]] = {
    find("clientId" -> clientId, "notification.notificationId" -> notificationId).map {
      _.headOption.map (cn => cn.notification)
    }
  }

  override def fetchOverThreshold(threshold: Int): Future[List[ClientOverThreshold]] = {
    import collection.BatchCommands.AggregationFramework._

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

}
