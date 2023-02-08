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

package uk.gov.hmrc.apinotificationqueue.service

import java.util.concurrent.TimeUnit

import akka.actor.ActorSystem
import javax.inject.{Inject, Singleton}
import org.joda.time.format.ISODateTimeFormat
import uk.gov.hmrc.apinotificationqueue.connector.EmailConnector
import uk.gov.hmrc.apinotificationqueue.model.{ApiNotificationQueueConfig, Email, SendEmailRequest}
import uk.gov.hmrc.apinotificationqueue.repository.{ClientOverThreshold, NotificationRepository}
import uk.gov.hmrc.customs.api.common.logging.CdsLogger

import scala.concurrent.ExecutionContext
import scala.concurrent.duration._

@Singleton
class WarningEmailPollingService @Inject()(notificationRepo: NotificationRepository,
                                           emailConnector: EmailConnector,
                                           actorSystem: ActorSystem,
                                           cdsLogger: CdsLogger,
                                           config: ApiNotificationQueueConfig)(implicit executionContext: ExecutionContext) {

  private val templateId = "customs_pull_notifications_warning"
  private val interval = config.emailConfig.notificationEmailInterval
  private val delay = config.emailConfig.notificationEmailDelay
  private val toAddress = config.emailConfig.notificationEmailAddress
  private val queueThreshold = config.emailConfig.notificationEmailQueueThreshold
  private val emailEnabled = config.emailConfig.notificationEmailEnabled

  if (emailEnabled) {
    actorSystem.scheduler.scheduleWithFixedDelay(Duration(delay, TimeUnit.SECONDS), Duration(interval, TimeUnit.MINUTES)) {
      () => {
        cdsLogger.debug(s"running warning email scheduler with delay of $delay, interval of $interval and queue threshold of $queueThreshold")
        notificationRepo.fetchOverThreshold(queueThreshold).map(results =>
          if (results.nonEmpty) {
            val sendEmailRequest = SendEmailRequest(List(Email(toAddress)), templateId, buildParameters(results, queueThreshold), force = false)
            cdsLogger.debug(s"sending email with ${results.size} clientId rows")
            emailConnector.send(sendEmailRequest)
          } else {
            cdsLogger.info(s"No notification warning email sent as no clients have more notifications than threshold of $queueThreshold")
          })
      }
    }
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
