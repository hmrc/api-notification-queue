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

import play.api.libs.json.{Format, Json, OFormat}
import play.api.libs.json.OFormat.given
import play.api.libs.json._
import uk.gov.hmrc.mongo.play.json.formats.MongoJavatimeFormats

import java.time.Instant
import java.util.UUID

object NotificationStatus extends Enumeration {
  val Unpulled: NotificationStatus.Value = Value("unpulled")
  val Pulled: NotificationStatus.Value = Value("pulled")
}

case class  NotificationId(notificationId: UUID) 
object NotificationId {
  implicit lazy val notificationIdJF: Format[NotificationId] = Format(
    (__ \ "notificationId").read[UUID].map(NotificationId(_)),
    (__ \ "notificationId").write[UUID].contramap(_.notificationId)
  )
}

case class NotificationWithIdOnly(notification: NotificationId)
object NotificationWithIdOnly {
  implicit lazy val notificationWithIdOnlyJF: Format[NotificationWithIdOnly] = Format(
    (__ \ "notification").read[NotificationId].map(NotificationWithIdOnly(_)),
    (__ \ "notification").write[NotificationId].contramap(_.notification)
  )
}

case class NotificationWithIdAndPulled(notification: NotificationId, pulled: Boolean)
object NotificationWithIdAndPulled {
  implicit val notificationWithIdAndPulledStatusJF: OFormat[NotificationWithIdAndPulled] = Json.format[NotificationWithIdAndPulled]
}

case class Notification(notificationId: UUID,
                        conversationId: UUID,
                        headers: Map[String, String],
                        payload: String,
                        dateReceived: Instant,
                        datePulled: Option[Instant])

object Notification {
  implicit val dateTimeJF: Format[Instant] = MongoJavatimeFormats.instantFormat
  implicit val notificationJF: OFormat[Notification] = Json.format[Notification]
}

case class Notifications(notifications: List[String])

object Notifications {
  implicit lazy val notificationsJF: Format[Notifications] = Format(
    (__ \ "notifications").read[List[String]].map(Notifications(_)),
    (__ \ "notifications").write[List[String]].contramap(_.notifications)
  )
}
