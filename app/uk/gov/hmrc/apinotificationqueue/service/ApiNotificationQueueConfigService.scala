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

package uk.gov.hmrc.apinotificationqueue.service

import cats.implicits._
import javax.inject.{Inject, Singleton}
import uk.gov.hmrc.apinotificationqueue.model.{ApiNotificationQueueConfig, EmailConfig}
import uk.gov.hmrc.customs.api.common.config.{ConfigValidatedNelAdaptor, CustomsValidatedNel}
import uk.gov.hmrc.customs.api.common.logging.CdsLogger

@Singleton
class ApiNotificationQueueConfigService @Inject()(configValidatedNel: ConfigValidatedNelAdaptor, cdsLogger: CdsLogger) extends ApiNotificationQueueConfig {

  private val root = configValidatedNel.root

  private val apiSubscriptionFieldsServiceUrlNel = configValidatedNel.service("api-subscription-fields").serviceUrl

  private case class ApiNotificationQueueConfigImpl(emailConfig: EmailConfig, apiSubscriptionFieldsServiceUrl: String, ttlInSeconds: Int) extends ApiNotificationQueueConfig

  private val emailServiceUrlNel = configValidatedNel.service("email").serviceUrl
  private val notificationEmailEnabledNel = root.boolean("notification.email.enabled")
  private val notificationEmailQueueThresholdNel = root.int("notification.email.queueThreshold")
  private val notificationEmailAddressNel = root.string("notification.email.address")
  private val notificationEmailIntervalNel = root.int("notification.email.interval")
  private val notificationEmailDelayNel = root.int("notification.email.delay")
  private val ttlInSecondsNel = configValidatedNel.root.int("ttlInSeconds")

  private val validatedEmailConfig: CustomsValidatedNel[EmailConfig] =
    (emailServiceUrlNel,
      notificationEmailEnabledNel,
      notificationEmailQueueThresholdNel,
      notificationEmailAddressNel,
      notificationEmailIntervalNel,
      notificationEmailDelayNel
    ) mapN EmailConfig

  private val validatedConfig =
    (validatedEmailConfig, apiSubscriptionFieldsServiceUrlNel, ttlInSecondsNel) mapN ApiNotificationQueueConfigImpl

  private val config = validatedConfig.fold({
    nel => // error case exposes nel (a NotEmptyList)
      val errorMsg = "\n" + nel.toList.mkString("\n")
      cdsLogger.error(errorMsg)
      throw new IllegalStateException(errorMsg)
      },
    config => config // success
  )

  override val emailConfig: EmailConfig = config.emailConfig

  override val apiSubscriptionFieldsServiceUrl: String = config.apiSubscriptionFieldsServiceUrl

  override val ttlInSeconds: Int = config.ttlInSeconds
}
