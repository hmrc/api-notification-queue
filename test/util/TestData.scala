/*
 * Copyright 2020 HM Revenue & Customs
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

package util

import java.util.UUID

import org.joda.time.{DateTime, DateTimeZone}
import uk.gov.hmrc.apinotificationqueue.controller.CustomHeaderNames.{X_CLIENT_ID_HEADER_NAME, X_CONVERSATION_ID_HEADER_NAME}
import uk.gov.hmrc.apinotificationqueue.model.{Email, Notification, NotificationId, NotificationWithIdAndPulled, SendEmailRequest}
import uk.gov.hmrc.apinotificationqueue.repository.{ClientNotification, ClientOverThreshold}
import util.TestData._

object TestData {

  val ClientId1 = "clientId1"
  val ClientId2 = "clientId2"
  val NotificationId1 = UUID.fromString("ea52e86c-3322-4a5b-8bf7-b2d7d6e3fa8d")
  val NotificationId2 = UUID.fromString("5d60bab0-b866-4179-ba5c-b8e19176cfd9")
  val NotificationId3 = UUID.fromString("8f879794-84c4-4a05-96e9-b9432240ff23")
  val Payload = "<foo></foo>"
  val ConversationId1 = "eaca01f9-ec3b-4ede-b263-61b626dde231"
  val ConversationId1Uuid = UUID.fromString(ConversationId1)
  val ConversationId2 = "47a1311c-2de0-403f-b1bb-b474da5eb0c6"
  val ConversationId2Uuid = UUID.fromString(ConversationId2)
  
  val Year = 2017
  val MonthOfYear = 7
  val DayOfMonth = 4
  val HourOfDay = 13
  val MinuteOfHour = 45
  val TimeReceived = new DateTime(Year, MonthOfYear, DayOfMonth, HourOfDay, MinuteOfHour, DateTimeZone.UTC)
  val LatestReceived = TimeReceived.plus(1)
  val TimePulled = LatestReceived.plus(1)

  val Headers = Map("h1" -> "v1", "h2" -> "v2", "X-Conversation-ID" -> ConversationId1)
  val Notification1 = Notification(NotificationId1, ConversationId1Uuid, Headers, Payload, TimeReceived, None)
  val Notification2 = Notification(NotificationId2, ConversationId1Uuid, Headers, Payload, LatestReceived, Some(TimePulled))
  val Notification3 = Notification(NotificationId3, ConversationId1Uuid, Headers, Payload, TimeReceived, None)
  val Client1Notification1 = ClientNotification(ClientId1, Notification1)
  val Client1Notification2 = ClientNotification(ClientId1, Notification2)

  val ClientOverThreshold1 = ClientOverThreshold(ClientId1, 2, TimeReceived, LatestReceived)

  val NotificationWithIdAndPulledStatus1 = NotificationWithIdAndPulled(NotificationId(NotificationId1), pulled = false)
  val NotificationWithIdAndPulledStatus2 = NotificationWithIdAndPulled(NotificationId(NotificationId2), pulled = true)

  val TestSendEmailRequest = SendEmailRequest(List(Email("some-email@domain.com")),
    "customs_pull_notifications_warning",
    Map("clientId_0" -> "clientId1",
      "notificationTotal_0" -> "2",
      "oldestNotification_0" -> "20170704T134500.000Z",
      "latestNotification_0" -> "20170704T134500.001Z",
      "queueThreshold" -> "2"),
    force = false)

  type EmulatedServiceFailure = UnsupportedOperationException
  val emulatedServiceFailure = new EmulatedServiceFailure("Emulated service failure.")

}

object RequestHeaders {

  lazy val X_CLIENT_ID_HEADER: (String, String) = X_CLIENT_ID_HEADER_NAME -> ClientId1

  lazy val X_CONVERSATION_ID_HEADER: (String, String) = X_CONVERSATION_ID_HEADER_NAME -> ConversationId1

  val LoggingHeaders: Seq[(String, String)] = Seq(X_CLIENT_ID_HEADER, X_CONVERSATION_ID_HEADER)
}
