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

package uk.gov.hmrc.apinotificationqueue.connector

import javax.inject.{Inject, Singleton}

import play.api.Logger
import play.api.libs.json.Json
import uk.gov.hmrc.apinotificationqueue.config.ServiceConfiguration
import uk.gov.hmrc.apinotificationqueue.model.Email
import uk.gov.hmrc.http.{HeaderCarrier, HttpResponse}
import uk.gov.hmrc.play.bootstrap.http.HttpClient

import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global

@Singleton
class EmailConnector @Inject()(http: HttpClient, config: ServiceConfiguration) {

  private val emailUrl = config.baseUrl("email")
  private implicit val hc = HeaderCarrier()

  def send(email: Email): Future[HttpResponse] = {

    Logger.debug(s"sending email: ${Json.toJson(email)}")

    http.POST[Email, HttpResponse](s"$emailUrl/hmrc/email", email)
      .recoverWith {
        case e: Throwable =>
          Logger.error(s"call to email service failed. url=$emailUrl", e)
          Future.failed(e)
      }

  }

}
