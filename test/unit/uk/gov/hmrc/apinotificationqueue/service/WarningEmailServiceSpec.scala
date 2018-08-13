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

package uk.gov.hmrc.apinotificationqueue.service

import org.joda.time.DateTime
import org.mockito.ArgumentCaptor
import org.mockito.ArgumentMatchers.{eq => ameq, _}
import org.mockito.Mockito._
import org.scalatest.concurrent.Eventually
import org.scalatest.mockito.MockitoSugar
import uk.gov.hmrc.apinotificationqueue.config.ServiceConfiguration
import uk.gov.hmrc.apinotificationqueue.connector.EmailConnector
import uk.gov.hmrc.apinotificationqueue.model.{Email, SendEmailRequest}
import uk.gov.hmrc.apinotificationqueue.repository.{ClientOverThreshold, NotificationRepository}
import uk.gov.hmrc.http.HttpResponse
import uk.gov.hmrc.play.test.UnitSpec

import scala.concurrent.Future

class WarningEmailServiceSpec extends UnitSpec with MockitoSugar with Eventually {

  trait Setup {

    val mockNotificationRepository = mock[NotificationRepository]
    val mockEmailConnector = mock[EmailConnector]
    val mockServiceConfiguration = mock[ServiceConfiguration]
    val warningEmailService = new WarningEmailService(mockNotificationRepository, mockEmailConnector, mockServiceConfiguration)

    val year = 2017
    val monthOfYear = 7
    val dayOfMonth = 4
    val hourOfDay = 13
    val minuteOfHour = 45
    val timeReceived = new DateTime(year, monthOfYear, dayOfMonth, hourOfDay, minuteOfHour)
    val latestReceived = timeReceived.plus(1)
    val clientId1 = "clientId1"
    val clientOverThreshold1 = ClientOverThreshold(clientId1, 2, timeReceived, latestReceived)

    val sendEmailRequest = SendEmailRequest(List(Email("some-email@address.com")),
      "customs_pull_notifications_warning",
      Map("clientId_0" -> "some-client-id",
        "notificationTotal_0" -> "some-total",
        "oldestNotification_0" -> "some-date",
        "latestNotification_0" -> "some-date",
        "queueThreshold" -> "some-threshold"),
      force = false)
  }

  "WarningEmailService" should {
    "send an email" in new Setup {
      when(mockServiceConfiguration.getInt("notification.email.queueThreshold")).thenReturn(2)
      when(mockServiceConfiguration.getString("notification.email.address")).thenReturn("some-email@address.com")
      when(mockEmailConnector.send(any[SendEmailRequest]())).thenReturn(Future.successful(mock[HttpResponse]))
      when(mockNotificationRepository.fetchOverThreshold(2)).thenReturn(Future.successful(List(clientOverThreshold1)))

      await(warningEmailService.sendEmail())

      val emailRequestCaptor: ArgumentCaptor[SendEmailRequest] = ArgumentCaptor.forClass(classOf[SendEmailRequest])
      verify(mockEmailConnector).send(emailRequestCaptor.capture())

      val request: SendEmailRequest = emailRequestCaptor.getValue

      request.to.head.value shouldBe "some-email@address.com"
      request.templateId shouldBe "customs_pull_notifications_warning"
      ((request.parameters.keySet -- sendEmailRequest.parameters.keySet)
        ++ (sendEmailRequest.parameters.keySet -- request.parameters.keySet)).size shouldBe 0
    }

    //TODO add unhappy path test

  }

}
