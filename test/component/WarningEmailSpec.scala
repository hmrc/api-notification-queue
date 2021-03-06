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

package component

import akka.util.Timeout
import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock
import com.github.tomakehurst.wiremock.client.WireMock._
import com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig
import org.scalatest._
import org.scalatest.concurrent.Eventually
import org.scalatest.time.{Seconds, Span}
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.Application
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.libs.json.Json
import play.api.test.Helpers
import play.api.test.Helpers.{ACCEPTED, await}
import reactivemongo.bson.BSONObjectID
import util.TestData._
import uk.gov.hmrc.apinotificationqueue.repository.{ClientNotification, MongoDbProvider}
import uk.gov.hmrc.mongo.ReactiveRepository
import uk.gov.hmrc.mongo.json.ReactiveMongoFormats

import scala.concurrent.ExecutionContext
import scala.concurrent.duration._

class WarningEmailSpec extends FeatureSpec
  with GivenWhenThen
  with Matchers
  with GuiceOneAppPerSuite
  with Eventually
  with BeforeAndAfterEach {

  private implicit val duration: Timeout = 5 seconds
  private val Port: Int = sys.env.getOrElse("WIREMOCK_SERVICE_PORT", "11111").toInt
  private val Host = "localhost"
  private val wireMockServer = new WireMockServer(wireMockConfig().port(Port))

  private val componentTestConfigs: Map[String, Any] = Map(
    "notification.email.queueThreshold" -> 2,
    "notification.email.enabled" -> true,
    "notification.email.address" -> "some-email@domain.com",
    "notification.email.interval" -> 1,
    "notification.email.delay" -> 3,
    "microservice.services.email.host" -> Host,
    "microservice.services.email.port" -> Port
  )

  implicit val ec: ExecutionContext = Helpers.stubControllerComponents().executionContext
  override implicit lazy val app: Application = new GuiceApplicationBuilder().configure(componentTestConfigs).build()

  private val repo = new ReactiveRepository[ClientNotification, BSONObjectID](
    collectionName = "notifications",
    mongo = app.injector.instanceOf[MongoDbProvider].mongo,
    domainFormat = ClientNotification.ClientNotificationJF, ReactiveMongoFormats.objectIdFormats) {
  }

  private val Wait = 10
  override implicit def patienceConfig: PatienceConfig = super.patienceConfig.copy(timeout = Span(Wait, Seconds))

  override def beforeEach() {
    setupEmailService()
    setupDatabase()
  }

  override def afterEach(): Unit = {
    await(repo.drop)
    wireMockServer.stop()
  }

  feature("Pull notifications warning email") {

    scenario("notifications breaching threshold") {
      Given("notifications breaching threshold")

      When("scheduler queries database")
      info("automatically occurs when app starts")

      Then("a warning email is sent")
      eventually(verify(1, postRequestedFor(urlEqualTo("/hmrc/email"))
        .withRequestBody(equalToJson(Json.toJson(TestSendEmailRequest).toString()))))
    }
  }

  private def setupEmailService(): Unit = {
    startMockServer()
    setupEmailServiceToReturn(ACCEPTED)
  }

  private def startMockServer() {
    if (!wireMockServer.isRunning) wireMockServer.start()
    WireMock.configureFor(Host, Port)
  }

  private def setupEmailServiceToReturn(status: Int): Unit = {
    stubFor(post(urlEqualTo("/hmrc/email")).
      willReturn(
        aResponse()
          .withStatus(status)))
  }

  private def setupDatabase(): Unit = {
    await(repo.drop)
    await(repo.insert(Client1Notification1))
    await(repo.insert(Client1Notification2))
  }
}
