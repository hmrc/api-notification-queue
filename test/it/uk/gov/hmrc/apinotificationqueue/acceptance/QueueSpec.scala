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

package uk.gov.hmrc.apinotificationqueue.acceptance

import org.scalatest.OptionValues._
import org.scalatest.{FeatureSpec, GivenWhenThen, Matchers}
import org.scalatestplus.play.guice._
import play.api.mvc.{AnyContentAsEmpty, Headers}
import play.api.test.FakeRequest
import play.api.test.Helpers._

class QueueSpec extends FeatureSpec with GivenWhenThen with Matchers with GuiceOneAppPerTest {

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
}
