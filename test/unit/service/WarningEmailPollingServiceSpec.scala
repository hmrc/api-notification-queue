/*
 * Copyright 2021 HM Revenue & Customs
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

package unit.service

import akka.actor.ActorSystem
import org.joda.time.DateTime
import org.junit.runner.RunWith
import org.mockito.ArgumentCaptor
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito._
import org.mockito.junit.MockitoJUnitRunner
import org.scalatest.concurrent.Eventually
import org.scalatestplus.mockito.MockitoSugar
import play.api.test.Helpers
import uk.gov.hmrc.apinotificationqueue.connector.EmailConnector
import uk.gov.hmrc.apinotificationqueue.model.{Email, EmailConfig, SendEmailRequest}
import uk.gov.hmrc.apinotificationqueue.repository.{ClientOverThreshold, NotificationRepository}
import uk.gov.hmrc.apinotificationqueue.service.{ApiNotificationQueueConfigService, WarningEmailPollingService}
import util.UnitSpec
import util.StubCdsLogger
import uk.gov.hmrc.play.bootstrap.config.ServicesConfig

import scala.concurrent.{ExecutionContext, Future}

class WarningEmailPollingServiceSpec extends UnitSpec
  with MockitoSugar
  with Eventually {

  trait Setup {

    implicit val ec: ExecutionContext = Helpers.stubControllerComponents().executionContext
    val emailConfig = EmailConfig("some-url", notificationEmailEnabled = true, 2, "some-email@address.com", 1, 0)

    val mockNotificationRepository = mock[NotificationRepository]
    val mockEmailConnector = mock[EmailConnector]
    val cdsLogger = new StubCdsLogger(mock[ServicesConfig])
    val mockConfig = mock[ApiNotificationQueueConfigService]
    val mockEmailConfig = mock[EmailConfig]
    val testActorSystem = ActorSystem("WarningEmailPollingService")

    val oneThousand = 1000
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

      when(mockConfig.emailConfig).thenReturn(emailConfig)
  }

  "WarningEmailPollingService" should {
    "send an email" in new Setup {
      when(mockNotificationRepository.fetchOverThreshold(2)).thenReturn(Future.successful(List(clientOverThreshold1)))
      new WarningEmailPollingService(mockNotificationRepository, mockEmailConnector, testActorSystem, cdsLogger, mockConfig)
      val emailRequestCaptor: ArgumentCaptor[SendEmailRequest] = ArgumentCaptor.forClass(classOf[SendEmailRequest])

      Thread.sleep(oneThousand)
      eventually(verify(mockEmailConnector).send(emailRequestCaptor.capture()))

      val request = emailRequestCaptor.getValue
      request.to.head.value shouldBe "some-email@address.com"
      request.templateId shouldBe "customs_pull_notifications_warning"
      ((request.parameters.keySet -- sendEmailRequest.parameters.keySet)
        ++ (sendEmailRequest.parameters.keySet -- request.parameters.keySet)).size shouldBe 0
    }

    "not send an email when no clients breach queue threshold" in new Setup {
      when(mockNotificationRepository.fetchOverThreshold(2)).thenReturn(Future.successful(List.empty))
      new WarningEmailPollingService(mockNotificationRepository, mockEmailConnector, testActorSystem, cdsLogger, mockConfig)

      Thread.sleep(oneThousand)
      verify(mockEmailConnector, never()).send(any[SendEmailRequest]())
    }

    "not send an email when email disabled in config" in new Setup {
      private val emailDisabledConfig = EmailConfig("some-url", notificationEmailEnabled = false, 2, "some-email@address.com", 1, 0)
      when(mockConfig.emailConfig).thenReturn(emailDisabledConfig)

      when(mockNotificationRepository.fetchOverThreshold(2)).thenReturn(Future.successful(List(clientOverThreshold1)))
      new WarningEmailPollingService(mockNotificationRepository, mockEmailConnector, testActorSystem, cdsLogger, mockConfig)

      Thread.sleep(oneThousand)
      verify(mockEmailConnector, never()).send(any[SendEmailRequest]())
    }
  }
}
