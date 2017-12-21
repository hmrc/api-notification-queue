/*
 * Copyright 2017 HM Revenue & Customs
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
import javax.inject.{Inject, Singleton}

import com.google.inject.ImplementedBy
import play.api.Logger
import play.api.libs.json.Json
import reactivemongo.api.Cursor
import reactivemongo.api.indexes.{Index, IndexType}
import reactivemongo.bson.BSONObjectID
import reactivemongo.play.json._
import uk.gov.hmrc.mongo.ReactiveRepository
import uk.gov.hmrc.mongo.json.ReactiveMongoFormats

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
@ImplementedBy(classOf[NotificationMongoRepository])
trait NotificationRepository {
  def save(clientId: String, notification: Notification): Future[Notification]

  def fetch(clientId: String, notificationId: UUID): Future[Option[Notification]]

  def fetch(clientId: String): Future[List[Notification]]

  def delete(clientId: String, notificationId: UUID): Future[Boolean]
}

@Singleton
class NotificationMongoRepository @Inject()(mongoDbProvider: MongoDbProvider)
  extends ReactiveRepository[ClientNotification, BSONObjectID]("notifications", mongoDbProvider.mongo,
    ClientNotification.ClientNotificationJF, ReactiveMongoFormats.objectIdFormats)
    with NotificationRepository
    with NotificationRepositoryErrorHandler {

  private implicit val format = ClientNotification.ClientNotificationJF

  override def indexes: Seq[Index] = Seq(
    Index(
      key = Seq("clientId" -> IndexType.Ascending),
      name = Some("clientId-Index"),
      unique = false
    ),
    Index(
      key = Seq("clientId" -> IndexType.Ascending, "notification.notificationId" -> IndexType.Ascending),
      name = Some("clientId-notificationId-Index"),
      unique = true
    )
  )

  override def save(clientId: String, notification: Notification): Future[Notification] = {
    Logger.debug(s"[save] clientId: $clientId")

    val clientNotification = ClientNotification(clientId, notification)

    lazy val errorMsg = s"Notification not saved for client $clientId"

    collection.insert(clientNotification).map {
      writeResult => handleSaveError(writeResult, errorMsg, notification)
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

  override def delete(clientId: String, notificationId: UUID): Future[Boolean] = {
    val selector = Json.obj("clientId" -> clientId, "notification.notificationId" -> notificationId)
    lazy val errorMsg = s"Could not delete entity for selector: $selector"
    collection.remove(selector).map(handleDeleteError(_, errorMsg))
  }

}
