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

import play.api.Logger
import reactivemongo.api.commands.WriteResult
import reactivemongo.api.indexes.{Index, IndexType}
import reactivemongo.bson.BSONObjectID
import uk.gov.hmrc.mongo.ReactiveRepository
import uk.gov.hmrc.mongo.json.ReactiveMongoFormats

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

trait NotificationRepository {
  def save(clientId: String, notification: Notification): Future[Notification]

  def fetch(clientId: String, notificationId: UUID): Future[Option[Notification]]

  def fetch(clientId: String): Future[Option[List[Notification]]]

  def delete(clientId: String, notificationId: UUID): Future[Boolean]
}

@Singleton
class NotificationMongoRepository @Inject()(mongoDbProvider: MongoDbProvider)
  extends ReactiveRepository[ClientNotification, BSONObjectID]("notifications", mongoDbProvider.mongo,
    ClientNotification.ClientNotificationJF, ReactiveMongoFormats.objectIdFormats)
    with NotificationRepository {

  private implicit val format = ClientNotification.ClientNotificationJF

  override def indexes: Seq[Index] = Seq(
    Index(key = Seq("clientId" -> IndexType.Ascending), name = Some("clientId-Index"), unique = false),
    Index(key = Seq("clientId" -> IndexType.Ascending, "notification.notificationId" -> IndexType.Ascending), name = Some("clientId-notificationId-Index"), unique = true)
  )

  override def save(clientId: String, notification: Notification): Future[Notification] = {
    Logger.debug(s"[save] clientId: $clientId")

    val clientNotification = ClientNotification(clientId, notification)

    collection.insert(clientNotification).map {
      writeResult =>
        lazy val recordNotInsertedError = s"Notification not inserted for client $clientId"
        writeResult.writeConcernError.fold(databaseAltered(writeResult, notification, recordNotInsertedError)){ writeConcernError =>
          val errMsg = s"Error inserting notification for clientId $clientId : ${writeConcernError.errmsg}"
          Logger.error(errMsg)
          throw new RuntimeException(errMsg)
        }
    }
  }

  //TODO
  override def fetch(clientId: String, notificationId: UUID): Future[Option[Notification]] = ???
  override def fetch(clientId: String): Future[Option[List[Notification]]] = ???
  override def delete(clientId: String, notificationId: UUID): Future[Boolean] = ???

  private def databaseAltered(writeResult: WriteResult, notification: Notification, errMsg: => String): Notification = {
    if (writeResult.n > 0) {
      notification
    } else {
      throw new RuntimeException(errMsg)
    }
  }

}
