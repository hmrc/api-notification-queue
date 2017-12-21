package uk.gov.hmrc.apinotificationqueue.acceptance

import org.scalatest.OptionValues._
import org.scalatest.{FeatureSpec, GivenWhenThen, Matchers}
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.mvc.{AnyContentAsEmpty, Headers}
import play.api.test.FakeRequest
import play.api.test.Helpers._

class QueueSpec extends FeatureSpec with GivenWhenThen with Matchers with GuiceOneAppPerSuite {


  feature("Post and retrieve a message from the queue") {
    info("As a 3rd Party system")
    info("I want to successfully persist a notification")
    info("So that I can retrieve it when needed ")

    scenario("3rd party system gets a message previously queued") {
      Given("a message has already been queued")
      val clientId = "aaaa"
      val xmlBody = <xml>
        <node>Stuff</node>
      </xml>
      val queueResponse = await(route(app = app, FakeRequest("POST", "/queue", Headers("x-client-id" -> clientId, "content-type" -> "application/xml"), AnyContentAsEmpty).withXmlBody(xmlBody)).value)
      val location = queueResponse.header.headers("Location")

      When("you make a get based on the location header")
      val result = route(app = app, FakeRequest("GET", location, Headers("x-client-id" -> clientId), AnyContentAsEmpty)).value

      Then("you will receive a 200 response")
      status(result) shouldBe OK
      And("the message will be the same")
      contentAsString(result) shouldBe xmlBody.toString()
    }
  }
}
