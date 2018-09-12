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

import uk.gov.hmrc.apinotificationqueue.model.{EmailConfig, FieldsConfigHolder}
import uk.gov.hmrc.customs.api.common.config.ConfigValidationNelAdaptor
import uk.gov.hmrc.customs.api.common.logging.CdsLogger

import scalaz.{NonEmptyList, Validation}
import scalaz.syntax.apply._
import scalaz.syntax.traverse._

@Singleton
class ApiNotificationQueueConfigService @Inject()(configValidationNel: ConfigValidationNelAdaptor, cdsLogger: CdsLogger) {

  private val root = configValidationNel.root

  private val apiSubscriptionFieldsServiceUrlNel = configValidationNel.service("api-subscription-fields").serviceUrl

  private val emailServiceUrlNel = configValidationNel.service("email").serviceUrl
  private val notificationEmailQueueThresholdNel = root.int("notification.email.queueThreshold")
  private val notificationEmailAddressNel = root.string("notification.email.address")
  private val notificationEmailIntervalNel = root.int("notification.email.interval")
  private val notificationEmailDelayNel = root.int("notification.email.delay")

  private val validatedEmailConfig: Validation[NonEmptyList[String], EmailConfig] =
    (emailServiceUrlNel |@|
      notificationEmailQueueThresholdNel |@|
      notificationEmailAddressNel |@|
      notificationEmailIntervalNel |@|
      notificationEmailDelayNel
    ) (EmailConfig.apply)

  private val validatedFieldsConfigHolder = apiSubscriptionFieldsServiceUrlNel.map(FieldsConfigHolder.apply)

  private val queueConfigHolder = (validatedEmailConfig |@|
    validatedFieldsConfigHolder
    ) (QueueConfigHolder.apply) fold(
    fail = { nel =>
      // error case exposes nel (a NotEmptyList)
      val errorMsg = nel.toList.mkString("\n", "\n", "")
      cdsLogger.error(errorMsg)
      throw new IllegalStateException(errorMsg)
    },
    succ = identity
  )

  val emailConfig = queueConfigHolder.emailConfig

  val fieldsConfigHolder = queueConfigHolder.fieldsConfigHolder

  private case class QueueConfigHolder(emailConfig: EmailConfig, fieldsConfigHolder: FieldsConfigHolder)

}
