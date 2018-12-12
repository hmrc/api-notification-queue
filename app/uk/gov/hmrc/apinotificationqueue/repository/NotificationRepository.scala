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

import java.util.UUID

import com.google.inject.ImplementedBy
import javax.inject.{Inject, Singleton}
import play.api.libs.json.{Format, Json}
import reactivemongo.api.Cursor
import reactivemongo.api.indexes.{Index, IndexType}
import reactivemongo.bson.{BSONDocument, BSONLong, BSONObjectID}
import reactivemongo.play.json._
import uk.gov.hmrc.apinotificationqueue.model.Notification
import uk.gov.hmrc.customs.api.common.logging.CdsLogger
import uk.gov.hmrc.mongo.ReactiveRepository
import uk.gov.hmrc.mongo.json.ReactiveMongoFormats

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

@ImplementedBy(classOf[NotificationMongoRepository])
trait NotificationRepository {
  def save(clientId: String, notification: Notification): Future[Notification]

  def fetch(clientId: String, notificationId: UUID): Future[Option[Notification]]

  def fetch(clientId: String): Future[List[Notification]]

  def fetchOverThreshold(threshold: Int): Future[List[ClientOverThreshold]]

  def delete(clientId: String, notificationId: UUID): Future[Boolean]

}

@Singleton
class NotificationMongoRepository @Inject()(mongoDbProvider: MongoDbProvider,
                                            notificationRepositoryErrorHandler: NotificationRepositoryErrorHandler,
                                            cdsLogger: CdsLogger)
  extends ReactiveRepository[ClientNotification, BSONObjectID]("notifications", mongoDbProvider.mongo,
    ClientNotification.ClientNotificationJF, ReactiveMongoFormats.objectIdFormats)
    with NotificationRepository {

  private implicit val format: Format[ClientNotification] = ClientNotification.ClientNotificationJF

  override def indexes: Seq[Index] = {
    val ttlValue = 600L //TODO MC should be in config

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
        key = Seq("dateReceived" -> IndexType.Descending),
        name = Some("dateReceived-Index"),
        unique = false,
        options = BSONDocument("expireAfterSeconds" -> BSONLong(ttlValue))
      )

    )
  }

  override def save(clientId: String, notification: Notification): Future[Notification] = {
    cdsLogger.debug(s"saving clientId: $clientId")

    val clientNotification = ClientNotification(clientId, notification)

    lazy val errorMsg = s"Notification not saved for client $clientId"

    collection.insert(clientNotification).map {
      writeResult => notificationRepositoryErrorHandler.handleSaveError(writeResult, errorMsg, notification)
    }
  }

  override def fetch(clientId: String, notificationId: UUID): Future[Option[Notification]] = {
    val selector = Json.obj("clientId" -> clientId, "notification.notificationId" -> notificationId)
    collection.find(selector).one[ClientNotification].map(_.map(cn => cn.notification))
  }

  override def fetch(clientId: String): Future[List[Notification]] = {
    val selector = Json.obj("clientId" -> clientId)
    collection.find(selector).cursor[ClientNotification]().collect[List](Int.MaxValue, Cursor.FailOnError[List[ClientNotification]]()).map{_.map(cn => cn.notification)}
  }

  override def fetchOverThreshold(threshold: Int): Future[List[ClientOverThreshold]] = {
    import collection.BatchCommands.AggregationFramework._

    collection.aggregate(
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
        .map(_.head[ClientOverThreshold])
  }

  override def delete(clientId: String, notificationId: UUID): Future[Boolean] = {
    val selector = Json.obj("clientId" -> clientId, "notification.notificationId" -> notificationId)
    lazy val errorMsg = s"Could not delete entity for selector: $selector"
    collection.remove(selector).map(notificationRepositoryErrorHandler.handleDeleteError(_, errorMsg))
  }

}
