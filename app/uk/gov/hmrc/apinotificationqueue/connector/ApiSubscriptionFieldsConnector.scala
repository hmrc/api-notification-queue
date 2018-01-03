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

import java.util.UUID
import javax.inject.Inject

import play.api.libs.json.Json
import uk.gov.hmrc.apinotificationqueue.config.ServiceConfiguration
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.bootstrap.http.HttpClient
import uk.gov.hmrc.play.http.logging.MdcLoggingExecutionContext._

import scala.concurrent.Future


case class ApiSubscriptionFieldResponse(clientId: String)

object ApiSubscriptionFieldResponse {
  implicit val rds = Json.format[ApiSubscriptionFieldResponse]
}

class ApiSubscriptionFieldsConnector @Inject()(http: HttpClient, config: ServiceConfiguration) {

  val serviceBaseUrl = config.baseUrl("api-subscription-fields")

  def lookupClientId(subscriptionFieldsId: UUID)(implicit hc: HeaderCarrier): Future[ApiSubscriptionFieldResponse] = {
    http.GET[ApiSubscriptionFieldResponse](s"$serviceBaseUrl/field/$subscriptionFieldsId")
  }

}
