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

package component

import org.scalatest.OptionValues._
import org.scalatest.{BeforeAndAfterEach, FeatureSpec, GivenWhenThen, Matchers}
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.Application
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.mvc.{AnyContentAsEmpty, Headers}
import play.api.test.FakeRequest
import play.api.test.Helpers.{await, _}
import reactivemongo.bson.BSONObjectID
import uk.gov.hmrc.apinotificationqueue.repository.{ClientNotification, MongoDbProvider}
import uk.gov.hmrc.mongo.ReactiveRepository
import uk.gov.hmrc.mongo.json.ReactiveMongoFormats

import scala.concurrent.ExecutionContext.Implicits.global

class QueueSpec extends FeatureSpec
  with GivenWhenThen
  with Matchers
  with GuiceOneAppPerSuite
  with BeforeAndAfterEach {

  private val componentTestConfigs: Map[String, Any] = Map(
    "notification.email.delay" -> 30
  )
  override implicit lazy val app: Application = new GuiceApplicationBuilder().configure(componentTestConfigs).build()
  private val repo = new ReactiveRepository[ClientNotification, BSONObjectID](
    collectionName = "notifications",
    mongo = app.injector.instanceOf[MongoDbProvider].mongo,
    domainFormat = ClientNotification.ClientNotificationJF, ReactiveMongoFormats.objectIdFormats) {
  }

  override def beforeEach() {
    await(repo.drop)
  }

  override def afterEach(): Unit = {
    await(repo.drop)
  }

  feature("Post, retrieve and delete a message from the queue") {
    info("As a 3rd Party system")
    info("I want to successfully persist a notification")
    info("So that I can retrieve it when needed")
    info("And delete it when needed")

    scenario("3rd party system gets a message previously queued") {
      Given("a message has already been queued")
      val clientId = "aaaa"
      val xmlBody = <xml><node>Stuff</node></xml>
      val queueResponse = await(route(app = app, FakeRequest(POST, "/queue", Headers("x-client-id" -> clientId, "content-type" -> "application/xml"), AnyContentAsEmpty).withXmlBody(xmlBody)).value)
      val location = queueResponse.header.headers("Location")

      When("you make a GET based on the location header")
      val result = route(app, FakeRequest(GET, location, Headers("x-client-id" -> clientId), AnyContentAsEmpty)).value

      Then("you will receive a 200 response")
      status(result) shouldBe OK
      And("the message will be the same")
      contentAsString(result) shouldBe xmlBody.toString()

      When("you make a DELETE based on the Location header")
      val deleteResult = route(app, FakeRequest(DELETE, location, Headers("x-client-id" -> clientId), AnyContentAsEmpty)).value

      Then("you will receive a 204 response")
      status(deleteResult) shouldBe NO_CONTENT
    }
  }

  feature("Post, pull and re-pull a message from the queue") {
    info("As a 3rd Party system")
    info("I want to successfully persist a notification")
    info("So that I can pull it when needed")
    info("And pull it again when needed")

    scenario("3rd party system gets a message previously queued") {
      Given("a message has already been queued")
      val clientId = "aaaa"
      val xmlBody = <xml><node>Stuff</node></xml>
      val queueResponse = await(route(app = app, FakeRequest(POST, "/queue", Headers("x-client-id" -> clientId, "content-type" -> "application/xml"), AnyContentAsEmpty).withXmlBody(xmlBody)).value)
      val location = queueResponse.header.headers("Location")
      val notificationId = location.substring(location.length() - 36)

      When("you make a GET based on the location header")
      val unpulledResult = route(app, FakeRequest(GET, s"/notifications/unpulled/$notificationId", Headers("x-client-id" -> clientId), AnyContentAsEmpty)).value

      Then("you will receive a 200 response")
      status(unpulledResult) shouldBe OK
      And("the message will be the same")
      contentAsString(unpulledResult) shouldBe xmlBody.toString()
      //TODO db update occurs after pulled request below without this sleep. Investigate potential for removal.
      Thread.sleep(500)

      When("you re-pull the message")
      val pulledResult = route(app, FakeRequest(GET, s"/notifications/pulled/$notificationId", Headers("x-client-id" -> clientId), AnyContentAsEmpty)).value

      Then("you will receive a 200 response")
      status(pulledResult) shouldBe OK
      And("you will receive the message again")
      contentAsString(unpulledResult) shouldBe xmlBody.toString()
    }
  }

  feature("Post, pull messages and then get a list of all previously pulled messages from the queue") {
    info("As a 3rd Party system")
    info("I want to successfully persist notifications")
    info("So that I can pull them when needed")
    info("And then pull a list of all previously pulled messages")

    scenario("3rd party system gets a list of previously pulled messages") {
      Given("two messages have already been queued")
      val clientId = "aaaa"
      val xmlBody = <xml><node>Stuff</node></xml>
      val queueResponse1 = await(route(app = app, FakeRequest(POST, "/queue", Headers("x-client-id" -> clientId, "content-type" -> "application/xml"), AnyContentAsEmpty).withXmlBody(xmlBody)).value)
      val location1 = queueResponse1.header.headers("Location")
      val notificationId1 = location1.substring(location1.length() - 36)
      val queueResponse2 = await(route(app = app, FakeRequest(POST, "/queue", Headers("x-client-id" -> clientId, "content-type" -> "application/xml"), AnyContentAsEmpty).withXmlBody(xmlBody)).value)
      val location2 = queueResponse2.header.headers("Location")
      val notificationId2 = location2.substring(location2.length() - 36)

      When("you make a GET based on the location header for the first message")
      val unpulledResult1 = route(app, FakeRequest(GET, s"/notifications/unpulled/$notificationId1", Headers("x-client-id" -> clientId), AnyContentAsEmpty)).value

      Then("you will receive a 200 response")
      status(unpulledResult1) shouldBe OK

      When("you make a GET based on the location header for the second message")
      val unpulledResult2 = route(app, FakeRequest(GET, s"/notifications/unpulled/$notificationId2", Headers("x-client-id" -> clientId), AnyContentAsEmpty)).value

      Then("you will receive a 200 response")
      status(unpulledResult2) shouldBe OK

      When("you get a list of all previously pulled messages")
      val pulledListResult = route(app, FakeRequest(GET, s"/notifications/pulled", Headers("x-client-id" -> clientId), AnyContentAsEmpty)).value

      Then("you will receive a list of two previously pulled messages")
      contentAsString(pulledListResult) shouldBe s"""{"notifications":["/notifications/pulled/$notificationId1","/notifications/pulled/$notificationId2"]}"""
    }
  }

  feature("Post and then get a list of all unpulled messages from the queue") {
    info("As a 3rd Party system")
    info("I want to successfully persist notifications")
    info("And then pull a list of all unpulled messages")

    scenario("3rd party system gets a list of unpulled messages") {
      Given("two messages have already been queued")
      val clientId = "aaaa"
      val xmlBody = <xml><node>Stuff</node></xml>
      val queueResponse1 = await(route(app = app, FakeRequest(POST, "/queue", Headers("x-client-id" -> clientId, "content-type" -> "application/xml"), AnyContentAsEmpty).withXmlBody(xmlBody)).value)
      val location1 = queueResponse1.header.headers("Location")
      val notificationId1 = location1.substring(location1.length() - 36)
      val queueResponse2 = await(route(app = app, FakeRequest(POST, "/queue", Headers("x-client-id" -> clientId, "content-type" -> "application/xml"), AnyContentAsEmpty).withXmlBody(xmlBody)).value)
      val location2 = queueResponse2.header.headers("Location")
      val notificationId2 = location2.substring(location2.length() - 36)

      When("you get a list of all unpulled messages")
      val pulledListResult = route(app, FakeRequest(GET, s"/notifications/unpulled", Headers("x-client-id" -> clientId), AnyContentAsEmpty)).value

      Then("you will receive a list of two unpulled messages")
      contentAsString(pulledListResult) shouldBe s"""{"notifications":["/notifications/unpulled/$notificationId1","/notifications/unpulled/$notificationId2"]}"""
    }
  }
}
