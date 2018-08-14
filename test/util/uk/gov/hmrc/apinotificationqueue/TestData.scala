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

  val clientId1 = "clientId1"
  val clientId2 = "clientId2"
  val notificationId1 = UUID.randomUUID()
  val notificationId2 = UUID.randomUUID()
  val notificationId3 = UUID.randomUUID()
  val payload = "<foo></foo>"

  val year = 2017
  val monthOfYear = 7
  val dayOfMonth = 4
  val hourOfDay = 13
  val minuteOfHour = 45
  val timeReceived = new DateTime(year, monthOfYear, dayOfMonth, hourOfDay, minuteOfHour)
  val latestReceived = timeReceived.plus(1)

  val headers = Map("h1" -> "v1", "h2" -> "v2")
  val notification1 = Notification(notificationId1, headers, payload, timeReceived)
  val notification2 = Notification(notificationId2, headers, payload, latestReceived)
  val notification3 = Notification(notificationId3, headers, payload, timeReceived)
  val client1Notification1 = ClientNotification(clientId1, notification1)
  val client1Notification2 = ClientNotification(clientId1, notification2)

  val clientOverThreshold1 = ClientOverThreshold(clientId1, 2, timeReceived, latestReceived)

  val sendEmailRequest = SendEmailRequest(List(Email("some-email@domain.com")),
    "customs_pull_notifications_warning",
    Map("clientId_0" -> "clientId1",
      "notificationTotal_0" -> "2",
      "oldestNotification_0" -> "20170704T134500.000+0100",
      "latestNotification_0" -> "20170704T134500.001+0100",
      "queueThreshold" -> "2"),
    force = false)

}
