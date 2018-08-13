package uk.gov.hmrc.apinotificationqueue.connector

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock
import com.github.tomakehurst.wiremock.client.WireMock._
import com.github.tomakehurst.wiremock.core.WireMockConfiguration
import org.joda.time.DateTime
import org.mockito.Mockito.when
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.mockito.MockitoSugar
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.Application
import play.api.http.Status.ACCEPTED
import play.api.inject.guice.GuiceApplicationBuilder
import uk.gov.hmrc.apinotificationqueue.config.ServiceConfiguration
import uk.gov.hmrc.apinotificationqueue.model.{Email, SendEmailRequest}
import uk.gov.hmrc.apinotificationqueue.repository.ClientOverThreshold
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.bootstrap.http.HttpClient
import uk.gov.hmrc.play.test.UnitSpec

class EmailConnectorSpec extends UnitSpec
  with ScalaFutures
  with GuiceOneAppPerSuite
  with MockitoSugar {

  private val emailPort = sys.env.getOrElse("WIREMOCK", "11111").toInt
  private val emailHost = "localhost"
  private val emailUrl = s"http://$emailHost:$emailPort"
  private val wireMockServer = new WireMockServer(WireMockConfiguration.wireMockConfig().port(emailPort))

  override implicit lazy val app: Application = GuiceApplicationBuilder().configure(Map(
      "microservice.services.email.host" -> emailHost,
      "microservice.services.email.port" -> emailPort,
      "microservice.services.email.context" -> "/hmrc/email"
    )).build()

  trait Setup {
    val mockHttpClient: HttpClient = mock[HttpClient]
    val mockServiceConfiguration: ServiceConfiguration = mock[ServiceConfiguration]
    when(mockServiceConfiguration.baseUrl("email")).thenReturn(emailUrl)

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

    val sendEmailRequest = SendEmailRequest(List(Email("some-email@address.com")), "some-template-id",
      Map("parameters" -> "some-parameter"), force = false)

    implicit val hc = HeaderCarrier()
    lazy val connector: EmailConnector = app.injector.instanceOf[EmailConnector]
  }

  "EmailConnector" should {

    "successfully email" in new Setup {
      wireMockServer.start()
      WireMock.configureFor(emailHost, emailPort)
      stubFor(post(urlEqualTo("/hmrc/email")).willReturn(aResponse().withStatus(ACCEPTED)))

      await(connector.send(sendEmailRequest))

      verify(1, postRequestedFor(urlEqualTo("/hmrc/email")))
    }

  }

}
