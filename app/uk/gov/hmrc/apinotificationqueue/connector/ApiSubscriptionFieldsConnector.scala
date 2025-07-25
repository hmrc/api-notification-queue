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

import play.api.libs.json.Format
import play.api.libs.json._
import uk.gov.hmrc.apinotificationqueue.logging.NotificationLogger
import uk.gov.hmrc.apinotificationqueue.model.ApiNotificationQueueConfig
import uk.gov.hmrc.http.HttpReads.Implicits._
import uk.gov.hmrc.http.client.HttpClientV2
import uk.gov.hmrc.http.{HeaderCarrier, HeaderNames, StringContextOps}

import java.util.UUID
import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}

case class ApiSubscriptionFieldResponse(clientId: String)

object ApiSubscriptionFieldResponse {
  implicit lazy val rds: Format[ApiSubscriptionFieldResponse] = Format(
      (__ \ "clientId").read[String].map(ApiSubscriptionFieldResponse(_)),
      (__ \ "clientId").write[String].contramap(_.clientId))
}

class ApiSubscriptionFieldsConnector @Inject()(http: HttpClientV2,
                                               config: ApiNotificationQueueConfig,
                                               logger: NotificationLogger)
                                              (implicit ec: ExecutionContext) {

  val serviceUrl: String = config.apiSubscriptionFieldsServiceUrl

  def lookupClientId(subscriptionFieldsId: UUID)(implicit hc: HeaderCarrier): Future[ApiSubscriptionFieldResponse] = {
    val url = s"$serviceUrl/$subscriptionFieldsId"
    val headerNames: Seq[String] = HeaderNames.explicitlyIncludedHeaders
    val headersToLog = hc.headers(headerNames) ++ hc.extraHeaders
    logger.debug(s"looking up clientId for $url", headersToLog)
    http.get(url"$url").execute[ApiSubscriptionFieldResponse]
  }

}
