/*
 * Copyright 2022 HM Revenue & Customs
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

package util.externalservices

import java.util.UUID

import com.github.tomakehurst.wiremock.client.WireMock._
import play.api.test.Helpers.OK
import util.ApiNotificationQueueExternalServicesConfig.ApiSubscriptionFieldsContext

trait ApiSubscriptionFieldsService {

  def startApiSubscriptionFieldsService(status: Int = OK, fieldsId: UUID, clientId: String): Unit =
    setupGetSubscriptionFieldsToReturn(status, fieldsId, clientId)

  def setupGetSubscriptionFieldsToReturn(status: Int, fieldsId: UUID, clientId: String): Unit = {

    stubFor(get(urlEqualTo(s"$ApiSubscriptionFieldsContext/$fieldsId"))
      .willReturn(aResponse().withStatus(status)
        .withBody( s"""{"clientId":"$clientId","apiContext":"context","fieldsId":"$fieldsId","apiVersion":"1.0"}""")))
  }

  def verifyGetSubscriptionFieldsCalled(fieldsId: UUID): Unit = {
    verify(1, getRequestedFor(urlEqualTo(s"$ApiSubscriptionFieldsContext/$fieldsId")))
  }

}
