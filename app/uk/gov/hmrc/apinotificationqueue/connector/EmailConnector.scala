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

package uk.gov.hmrc.apinotificationqueue.connector

import javax.inject.{Inject, Singleton}
import play.api.libs.json.Json
import uk.gov.hmrc.apinotificationqueue.model.{ApiNotificationQueueConfig, SendEmailRequest}
import uk.gov.hmrc.customs.api.common.logging.CdsLogger
import uk.gov.hmrc.http.{HeaderCarrier, HttpResponse}
import uk.gov.hmrc.http.HttpClient
import uk.gov.hmrc.http.HttpReads.Implicits._

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class EmailConnector @Inject()(http: HttpClient, config: ApiNotificationQueueConfig, cdsLogger: CdsLogger)(implicit ec: ExecutionContext) {

  private val emailUrl = config.emailConfig.emailServiceUrl
  private implicit val hc: HeaderCarrier = HeaderCarrier()

  def send(sendEmailRequest: SendEmailRequest): Future[Unit] = {

    cdsLogger.info(s"sending notification warnings email: ${Json.toJson(sendEmailRequest)}")

    http.POST[SendEmailRequest, HttpResponse](s"$emailUrl", sendEmailRequest).map { response =>
      cdsLogger.debug(s"response status from email service was ${response.status}")
    }
    .recover {
      case e: Throwable =>
        cdsLogger.error(s"call to email service failed. url=$emailUrl", e)
    }
  }
}
