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

package uk.gov.hmrc.apinotificationqueue.service

import java.util.UUID
import javax.inject.{Inject, Singleton}

import uk.gov.hmrc.apinotificationqueue.model.Notification
import uk.gov.hmrc.apinotificationqueue.repository.NotificationRepository

import scala.concurrent.Future

@Singleton()
class QueueService @Inject()(notificationRepo: NotificationRepository) {

  def get(clientId: String): Future[List[Notification]] = {
    notificationRepo.fetch(clientId)
  }

  def get(clientId: String, id: UUID): Future[Option[Notification]] = {
    notificationRepo.fetch(clientId, id)
  }

  def delete(clientId: String, id: UUID): Future[Boolean] = {
    notificationRepo.delete(clientId, id)
  }

  def save(clientId: String, message: Notification): Future[Notification] = {
    notificationRepo.save(clientId, message)
  }

}
