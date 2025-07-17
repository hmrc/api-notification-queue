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

package uk.gov.hmrc.apinotificationqueue.connector

import play.api.http.Status.{ACCEPTED, OK}
import play.api.libs.json.Json
import play.api.libs.ws.JsonBodyWritables.writeableOf_JsValue
import play.api.mvc.Results.BadRequest
import uk.gov.hmrc.apinotificationqueue.model.{ApiNotificationQueueConfig, SendEmailRequest}
import uk.gov.hmrc.customs.api.common.logging.CdsLogger
import uk.gov.hmrc.http.HttpReads.Implicits._
import uk.gov.hmrc.http.client.HttpClientV2
import uk.gov.hmrc.http.{HeaderCarrier, HttpResponse, StringContextOps}

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class EmailConnector @Inject()(http: HttpClientV2, config: ApiNotificationQueueConfig, cdsLogger: CdsLogger)(implicit ec: ExecutionContext) {

  private val emailUrl = config.emailConfig.emailServiceUrl
  private implicit val hc: HeaderCarrier = HeaderCarrier()

  def send(sendEmailRequest: SendEmailRequest): Future[Unit] = {
    cdsLogger.info(s"sending notification warnings email:: ${Json.toJson(sendEmailRequest)}")

    http.post(url"$emailUrl").withBody(Json.toJson(sendEmailRequest)).execute[HttpResponse].map { response =>
      response.status match {
        case OK | ACCEPTED => cdsLogger.debug(s"response status from email service was ${response.status}")
        case _  => cdsLogger.error(s"call to email service failed. url=$emailUrl, with response=${response.body}")
      }
    }
  }
}
