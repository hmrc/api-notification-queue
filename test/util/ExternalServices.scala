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

package util

import com.github.tomakehurst.wiremock.client.WireMock._
import com.github.tomakehurst.wiremock.verification.LoggedRequest
import org.scalatest.Suite
import uk.gov.hmrc.http.test.WireMockSupport

import java.util

trait ExternalClientService {
  self: Suite with WireMockSupport =>
  private val urlMatchingRequestPath = urlMatching(ExternalServicesConfiguration.ExternalClientServiceContext)

  def stubExternalClientService(status: Int): Unit =
    wireMockServer.stubFor(post(urlMatchingRequestPath).
      willReturn(
        aResponse()
          .withStatus(status)
      )
    )

  def stubExternalClientService(urlMatching: String,  status: Int): Unit =
    wireMockServer.stubFor(post(urlMatching).
      willReturn(
        aResponse()
          .withStatus(status)
      )
    )

  def getTheCallMadeToExternalSystem() :  LoggedRequest = {
    wireMockServer.findAll(postRequestedFor(urlMatchingRequestPath)).get(0)
  }

  def getTheCallMadeToExternalSystem(forUrl: String): util.List[LoggedRequest] = {
    wireMockServer.findAll(postRequestedFor(urlMatching(forUrl)))
  }

  def verifyExternalClientServiceNotCalled(): Unit = verify(0, postRequestedFor(urlMatchingRequestPath))

}

  object ExternalServicesConfiguration {
  val Protocol = "http"
  val Port: Int = sys.env.getOrElse("WIREMOCK_SERVICE_LOCATOR_PORT", "6001").toInt
  val Host = "localhost"
  val ExternalClientServiceContext = "/some/where/over/the/rainbow"
  val ExternalClientAuth: String = "Basic dXNlcjpwYXNzd29yZA=="
}

object ExternalServicesConfig {
  val Port: Int = sys.env.getOrElse("WIREMOCK_SERVICE_LOCATOR_PORT", "6001").toInt
  val Host = "localhost"
  val MdgSuppDecServiceContext = "/mdgSuppDecService/submitdeclaration"
  val AuthToken: String = "auth-token"
}

