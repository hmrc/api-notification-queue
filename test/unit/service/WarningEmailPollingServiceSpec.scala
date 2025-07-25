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

package unit.service

import org.apache.pekko.actor.ActorSystem
import org.mockito.ArgumentCaptor
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito._
import org.scalatest.concurrent.Eventually
import org.scalatestplus.mockito.MockitoSugar
import play.api.libs.json.{JsError, JsSuccess, JsValue, Json}
import play.api.test.Helpers
import uk.gov.hmrc.apinotificationqueue.connector.EmailConnector
import uk.gov.hmrc.apinotificationqueue.model.{Email, EmailConfig, SendEmailRequest}
import uk.gov.hmrc.apinotificationqueue.repository.{ClientOverThreshold, NotificationRepository}
import uk.gov.hmrc.apinotificationqueue.service.{ApiNotificationQueueConfigService, WarningEmailPollingService}
import uk.gov.hmrc.play.bootstrap.config.ServicesConfig
import util.{StubCdsLogger, UnitSpec}

import java.time.temporal.ChronoUnit
import java.time.{ZoneId, ZonedDateTime}
import scala.concurrent.{ExecutionContext, Future}

class WarningEmailPollingServiceSpec extends UnitSpec
  with MockitoSugar
  with Eventually {

  trait Setup {

    implicit val ec: ExecutionContext = Helpers.stubControllerComponents().executionContext
    val emailConfig = EmailConfig("localhost:6001", notificationEmailEnabled = true, 2, "some-email@address.com", 1, 0)

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
    val timeReceived = ZonedDateTime.of(year, monthOfYear, dayOfMonth, hourOfDay, minuteOfHour, 0, 0, ZoneId.of("UTC")).toInstant
    val latestReceived = timeReceived.plus(1, ChronoUnit.MILLIS)
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

      eventually {
        verify(mockEmailConnector).send(emailRequestCaptor.capture())

        val request = emailRequestCaptor.getValue
        request.to.head.value shouldBe "some-email@address.com"
        request.templateId shouldBe "customs_pull_notifications_warning"
        ((request.parameters.keySet -- sendEmailRequest.parameters.keySet)
          ++ (sendEmailRequest.parameters.keySet -- request.parameters.keySet)).size shouldBe 0
      }
    }

    "not send an email when no clients breach queue threshold" in new Setup {
      when(mockNotificationRepository.fetchOverThreshold(2)).thenReturn(Future.successful(List.empty))
      new WarningEmailPollingService(mockNotificationRepository, mockEmailConnector, testActorSystem, cdsLogger, mockConfig)

      Thread.sleep(oneThousand)
      verify(mockEmailConnector, never()).send(any[SendEmailRequest]())
    }

    "ClientOverThreshold JSON formatter" should {
      "serialize and deserialize correctly" in new Setup {
        val json: JsValue = Json.toJson(clientOverThreshold1)
        val parsed = json.validate[ClientOverThreshold]
        parsed match {
          case JsSuccess(result, _)=>
            result shouldEqual clientOverThreshold1
          case JsError(errors) =>
            fail("Deserialization failed:" + errors.mkString(", "))
        }
      }
    }

    "not send an email when email disabled in config" in new Setup {
      private val emailDisabledConfig = EmailConfig("localhost:6001", notificationEmailEnabled = false, 2, "some-email@address.com", 1, 0)
      when(mockConfig.emailConfig).thenReturn(emailDisabledConfig)

      when(mockNotificationRepository.fetchOverThreshold(2)).thenReturn(Future.successful(List(clientOverThreshold1)))
      new WarningEmailPollingService(mockNotificationRepository, mockEmailConnector, testActorSystem, cdsLogger, mockConfig)

      Thread.sleep(oneThousand)
      verify(mockEmailConnector, never()).send(any[SendEmailRequest]())
    }
  }
}
