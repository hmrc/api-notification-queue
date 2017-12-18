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

import scala.concurrent.Future

trait MessageRepository {
  def save(clientId: String, message: Message): Future[Message]

  def fetch(clientId: String, messageId: UUID): Future[Option[Message]]

  def fetch(clientId: String): Future[Option[List[Message]]]

  def delete(clientId: String, messageId: UUID): Future[Boolean]
}
