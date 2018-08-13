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

import javax.inject.{Inject, Singleton}

import org.joda.time.format.ISODateTimeFormat
import play.api.Logger
import uk.gov.hmrc.apinotificationqueue.config.ServiceConfiguration
import uk.gov.hmrc.apinotificationqueue.connector.EmailConnector
import uk.gov.hmrc.apinotificationqueue.model.{Email, SendEmailRequest}
import uk.gov.hmrc.apinotificationqueue.repository.{ClientOverThreshold, NotificationRepository}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

@Singleton
class WarningEmailService @Inject()(notificationRepo: NotificationRepository,
                                    emailConnector: EmailConnector,
                                    config: ServiceConfiguration) {

  private val templateId = "customs_pull_notifications_warning"

  def sendEmail(): Future[Unit] = {
    val toAddress = config.getString("notification.email.address")
    val queueThreshold = config.getInt("notification.email.queueThreshold")

    val futureClients = notificationRepo.fetchOverThreshold(queueThreshold)

    futureClients.map(results =>
      if (results.nonEmpty) {
        val sendEmailRequest = SendEmailRequest(List(Email(toAddress)), templateId, buildParameters(results, queueThreshold), force = false)
        emailConnector.send(sendEmailRequest).map { response =>
          Logger.debug(s"response status from email service was ${response.status}")
        }
      } else {
        Logger.info(s"No notification warning email sent as no clients have more notifications than threshold of $queueThreshold")
      }
    )
  }

  private def buildParameters(results: List[ClientOverThreshold], queueThreshold: Int): Map[String, String] = {
    Map("queueThreshold" -> queueThreshold.toString) ++
      results.zipWithIndex.flatMap { case (client, idx) =>
        Map(s"clientId_$idx" -> client.clientId,
            s"notificationTotal_$idx" -> client.notificationTotal.toString,
            s"oldestNotification_$idx" -> client.oldestNotification.toString(ISODateTimeFormat.basicDateTime()),
            s"latestNotification_$idx" -> client.latestNotification.toString(ISODateTimeFormat.basicDateTime()))
      }
  }
}
