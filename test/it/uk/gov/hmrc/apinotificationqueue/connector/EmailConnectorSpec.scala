package uk.gov.hmrc.apinotificationqueue.connector

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock
import com.github.tomakehurst.wiremock.client.WireMock.{aResponse, post, stubFor, urlEqualTo}
import com.github.tomakehurst.wiremock.core.WireMockConfiguration
import org.joda.time.DateTime
import org.mockito.Mockito.when
import org.scalatest.BeforeAndAfterEach
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.mockito.MockitoSugar
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import uk.gov.hmrc.apinotificationqueue.config.ServiceConfiguration
import uk.gov.hmrc.apinotificationqueue.model.{Email, SendEmailRequest}
import uk.gov.hmrc.apinotificationqueue.repository.ClientOverThreshold
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.bootstrap.http.HttpClient
import uk.gov.hmrc.play.http.ws.WSHttp
import uk.gov.hmrc.play.test.UnitSpec

class EmailConnectorSpec extends UnitSpec
  with ScalaFutures
  with BeforeAndAfterEach
  with GuiceOneAppPerSuite
  with MockitoSugar {

  private val emailPort = sys.env.getOrElse("WIREMOCK", "11112").toInt
  private val emailHost = "localhost"
  private val emailUrl = s"http://$emailHost:$emailPort"
  private val wireMockServer = new WireMockServer(WireMockConfiguration.wireMockConfig().port(emailPort))


  private class TestHttpClient extends HttpClient with WSHttp {
    override val hooks = Seq.empty
  }

  trait Setup {
    val serviceConfiguration = mock[ServiceConfiguration]
    when(serviceConfiguration.baseUrl("email")).thenReturn(emailUrl)

    private val year = 2017
    private val monthOfYear = 7
    private val dayOfMonth = 4
    private val hourOfDay = 13
    private val minuteOfHour = 45
    private val oldestReceived = new DateTime(year, monthOfYear, dayOfMonth, hourOfDay, minuteOfHour)
    private val latestReceived = oldestReceived.plusHours(1)

    val clientOverThreshold1 = ClientOverThreshold("clientId1", 11, oldestReceived, latestReceived)
    val clientOverThreshold2 = ClientOverThreshold("clientId2", 12, oldestReceived, latestReceived)
    val clientsOverThreshold = List(clientOverThreshold1, clientOverThreshold2)

    val sendEmailRequest = SendEmailRequest(List(Email("some-email@address.com")),
      "some-template-id",
      Map("clientId_0" -> "clientId1",
          "notificationTotal_0" -> "11",
          "oldestNotification_0" -> "2011-08-08T15:38:09.747+01:00",
          "latestNotification_0" -> "2018-08-08T10:02:10.627+01:00"),
      force = false)

    implicit val hc = HeaderCarrier()
    val connector = new EmailConnector(new TestHttpClient(), serviceConfiguration)
  }

  override def beforeEach() {
    wireMockServer.start()
    WireMock.configureFor(emailHost, emailPort)
  }

  override def afterEach() {
    wireMockServer.stop()
  }

  "EmailConnector" should {

    "successfully email" in new Setup {
      stubFor(post(urlEqualTo("/hmrc/email")).willReturn(aResponse().withStatus(202)))

      val result = await(connector.send(sendEmailRequest))

      result.status shouldBe 202
    }

    //TODO add unhappy path test
  }

}
