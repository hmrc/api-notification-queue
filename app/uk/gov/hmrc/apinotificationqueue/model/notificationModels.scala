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

package uk.gov.hmrc.apinotificationqueue.model

import org.joda.time.DateTime
import play.api.libs.json.{Format, Json, OFormat}
import uk.gov.hmrc.mongo.play.json.formats.{MongoJodaFormats, MongoUuidFormats}

import java.util.UUID

object NotificationStatus extends Enumeration {
  val Unpulled: NotificationStatus.Value = Value("unpulled")
  val Pulled: NotificationStatus.Value = Value("pulled")
}

case class  NotificationId(notificationId: UUID) extends AnyVal
object NotificationId {
  implicit val notificationIdJF: OFormat[NotificationId] = Json.format[NotificationId]
}

case class NotificationWithIdOnly(notification: NotificationId)
object NotificationWithIdOnly {
  implicit val notificationWithIdOnlyJF: OFormat[NotificationWithIdOnly] = Json.format[NotificationWithIdOnly]
}

case class NotificationWithIdAndPulled(notification: NotificationId, pulled: Boolean)
object NotificationWithIdAndPulled {
  implicit val notificationWithIdAndPulledStatusJF: OFormat[NotificationWithIdAndPulled] = Json.format[NotificationWithIdAndPulled]
}

case class Notification(notificationId: UUID,
                        conversationId: UUID,
                        headers: Map[String, String],
                        payload: String,
                        dateReceived: DateTime,
                        datePulled: Option[DateTime])

object Notification {
  implicit val dateTimeJF: Format[DateTime] = MongoJodaFormats.dateTimeFormat
  implicit val notificationJF: OFormat[Notification] = Json.format[Notification]
}

case class Notifications(notifications: List[String])

object Notifications {
  implicit val notificationsJF: OFormat[Notifications] = Json.format[Notifications]
}
