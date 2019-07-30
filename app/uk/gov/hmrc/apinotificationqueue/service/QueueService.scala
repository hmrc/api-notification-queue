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

package uk.gov.hmrc.apinotificationqueue.service

import java.util.UUID

import javax.inject.{Inject, Singleton}
import uk.gov.hmrc.apinotificationqueue.model.{Notification, NotificationStatus, NotificationWithIdAndPulledStatus, NotificationWithIdOnly}
import uk.gov.hmrc.apinotificationqueue.repository.NotificationRepository

import scala.concurrent.{ExecutionContext, Future}

@Singleton()
class QueueService @Inject()(notificationRepo: NotificationRepository)(implicit ec:ExecutionContext) {

  def get(clientId: String, notificationStatus: Option[NotificationStatus.Value]): Future[List[NotificationWithIdOnly]] = {
    notificationRepo.fetchNotificationIds(clientId, notificationStatus)
  }

  def getByConversationId(clientId: String, conversationId: UUID): Future[List[NotificationWithIdAndPulledStatus]] = {
    notificationRepo.fetchNotificationIds(clientId, conversationId)
  }

  def getByConversationId(clientId: String, conversationId: UUID, notificationStatus: NotificationStatus.Value): Future[List[NotificationWithIdOnly]] = {
    notificationRepo.fetchNotificationIds(clientId, conversationId, notificationStatus: NotificationStatus.Value)
  }

  def get(clientId: String, notificationId: UUID): Future[Option[Notification]] = {
    notificationRepo.fetch(clientId, notificationId)
  }
  
  def delete(clientId: String, notificationId: UUID): Future[Boolean] = {
    notificationRepo.delete(clientId, notificationId)
  }

  def save(clientId: String, message: Notification): Future[Notification] = {
    notificationRepo.save(clientId, message)
  }

  def update(clientId: String, message: Notification): Future[Notification] = {
    notificationRepo.update(clientId, message)
  }

}
