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

import play.api.Logger
import uk.gov.hmrc.apinotificationqueue.config.ServiceConfiguration
import uk.gov.hmrc.apinotificationqueue.connector.EmailConnector
import uk.gov.hmrc.apinotificationqueue.model.Email
import uk.gov.hmrc.apinotificationqueue.repository.NotificationRepository

import scala.concurrent.ExecutionContext.Implicits.global

@Singleton
class WarningEmailService @Inject()(notificationRepo: NotificationRepository,
                                    emailConnector: EmailConnector,
                                    config: ServiceConfiguration) {

  private val toAddress = config.getString("notification.email.address")
  private val templateId = config.getString("notification.email.templateId")
  private val threshold = config.getInt("notification.email.threshold")

  def sendEmail(): Unit = {
    val futureClients = notificationRepo.fetchOverThreshold(threshold)

    futureClients.map(results =>
      if (results.nonEmpty) {
        Logger.info("Sending email with notification warnings")
        val email = Email(List(toAddress), templateId, results, threshold)
        emailConnector.send(email)
      } else {
        Logger.info(s"No notification warning email sent as no clients have more notifications than threshold of $threshold")
      }
    )

  }

}
