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

package uk.gov.hmrc.apinotificationqueue

import java.util.UUID

import org.joda.time.DateTime
import uk.gov.hmrc.apinotificationqueue.model.{Email, Notification, SendEmailRequest}
import uk.gov.hmrc.apinotificationqueue.repository.{ClientNotification, ClientOverThreshold}

object TestData {

  val ClientId1 = "clientId1"
  val ClientId2 = "clientId2"
  val NotificationId1 = UUID.randomUUID()
  val NotificationId2 = UUID.randomUUID()
  val NotificationId3 = UUID.randomUUID()
  val Payload = "<foo></foo>"

  val Year = 2017
  val MonthOfYear = 7
  val DayOfMonth = 4
  val HourOfDay = 13
  val MinuteOfHour = 45
  val TimeReceived = new DateTime(Year, MonthOfYear, DayOfMonth, HourOfDay, MinuteOfHour)
  val LatestReceived = TimeReceived.plus(1)

  val Headers = Map("h1" -> "v1", "h2" -> "v2")
  val Notification1 = Notification(NotificationId1, Headers, Payload, TimeReceived)
  val Notification2 = Notification(NotificationId2, Headers, Payload, LatestReceived)
  val Notification3 = Notification(NotificationId3, Headers, Payload, TimeReceived)
  val Client1Notification1 = ClientNotification(ClientId1, Notification1)
  val Client1Notification2 = ClientNotification(ClientId1, Notification2)

  val ClientOverThreshold1 = ClientOverThreshold(ClientId1, 2, TimeReceived, LatestReceived)

  val TestSendEmailRequest = SendEmailRequest(List(Email("some-email@domain.com")),
    "customs_pull_notifications_warning",
    Map("clientId_0" -> "clientId1",
      "notificationTotal_0" -> "2",
      "oldestNotification_0" -> "20170704T134500.000+0100",
      "latestNotification_0" -> "20170704T134500.001+0100",
      "queueThreshold" -> "2"),
    force = false)

}
