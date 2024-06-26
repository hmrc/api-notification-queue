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

import play.api.http.Status.NOT_FOUND
import uk.gov.hmrc.apinotificationqueue.connector.ApiSubscriptionFieldsConnector
import uk.gov.hmrc.http.{HeaderCarrier, UpstreamErrorResponse}

import java.util.UUID
import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}

class ApiSubscriptionFieldsService @Inject()(apiSubscriptionFieldsConnector: ApiSubscriptionFieldsConnector)
                                            (implicit ec: ExecutionContext) {

  def getClientId(fieldsId: UUID)(implicit hc: HeaderCarrier): Future[Option[String]] = {
    apiSubscriptionFieldsConnector.lookupClientId(fieldsId).map(resp => Some(resp.clientId)) recover {
      case error: UpstreamErrorResponse => if (error.statusCode == NOT_FOUND) {
        None
      } else {
        throw error
      }
    }
  }
}
