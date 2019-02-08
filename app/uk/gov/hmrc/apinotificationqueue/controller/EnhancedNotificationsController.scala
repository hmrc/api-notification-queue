/*
 * Copyright 2019 HM Revenue & Customs
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

package uk.gov.hmrc.apinotificationqueue.controller

import java.util.UUID

import akka.util.ByteString
import javax.inject.{Inject, Singleton}
import org.joda.time.DateTime
import org.joda.time.DateTimeZone.UTC
import play.api.http.HttpEntity
import play.api.libs.concurrent.Execution.Implicits.defaultContext
import play.api.libs.json.Json
import play.api.mvc._
import uk.gov.hmrc.apinotificationqueue.controller.CustomHeaderNames.X_CLIENT_ID_HEADER_NAME
import uk.gov.hmrc.apinotificationqueue.logging.NotificationLogger
import uk.gov.hmrc.apinotificationqueue.model.NotificationStatus._
import uk.gov.hmrc.apinotificationqueue.model.{Notification, NotificationStatus, Notifications}
import uk.gov.hmrc.apinotificationqueue.service.{ApiSubscriptionFieldsService, QueueService}
import uk.gov.hmrc.customs.api.common.controllers.ErrorResponse
import uk.gov.hmrc.customs.api.common.controllers.ErrorResponse.{ErrorNotFound, errorBadRequest}
import uk.gov.hmrc.play.bootstrap.controller.BaseController

import scala.concurrent.Future

@Singleton
class EnhancedNotificationsController @Inject()(queueService: QueueService,
                                                fieldsService: ApiSubscriptionFieldsService,
                                                idGenerator: NotificationIdGenerator,
                                                dateTimeProvider: DateTimeProvider,
                                                logger: NotificationLogger) extends BaseController {

  private val MISSING_CLIENT_ID_ERROR = s"$X_CLIENT_ID_HEADER_NAME required"

  private val badRequestPulledText = "Notification has been pulled"
  private val badRequestUnpulledText = "Notification is unpulled"

  def getPulledByClientId: Action[AnyContent] = pullByClientId(Pulled)
  def getUnpulledByClientId: Action[AnyContent] = pullByClientId(Unpulled)

  private def pullByClientId(notificationStatus: NotificationStatus.Value): Action[AnyContent] = Action.async { implicit request =>

    val headers = request.headers
    logger.info(s"listing $notificationStatus notifications", headers.headers)
    validateHeader(request.headers, s"get $notificationStatus by client id") match {
      case Left(errorResponse) => Future.successful(errorResponse.XmlResult)
      case Right(clientId) =>
        val notificationIdPaths: Future[List[String]] =
          for {
            notificationIds <- queueService.get(clientId, Some(notificationStatus))
          } yield notificationIds.map(s"/notifications/$notificationStatus/" + _.notification.notificationId.toString)

        notificationIdPaths.map {idPaths =>
          val json = Json.toJson(Notifications(idPaths))
          logger.debug(s"listing $notificationStatus notifications $json", headers.headers)
          Ok(json)
        }
    }
  }

  def unpulled(id: UUID): Action[AnyContent] = pull(id, Unpulled)
  def pulled(id: UUID): Action[AnyContent] = pull(id, Pulled)

  private def pull(id: UUID, notificationStatus: NotificationStatus.Value): Action[AnyContent] = Action.async { implicit request =>

    def datePulledResult(n: Notification, clientId: String, headers: Headers) = {
      n.datePulled match {
        case Some(_) if notificationStatus == Unpulled =>
          logger.error(s"Notification has been pulled for id ${id.toString}", headers.headers)
          errorBadRequest(badRequestPulledText).XmlResult
        case Some(_) if notificationStatus == Pulled =>
          logger.debug(s"Pulling pulled notification for id ${id.toString}", headers.headers)
          result(n)
        case None if notificationStatus == Unpulled =>
          logger.debug(s"Pulling unpulled notification for id ${id.toString}", headers.headers)
          queueService.update(clientId, n.copy(datePulled = Some(dateTimeProvider.now())))
          result(n)
        case None if notificationStatus == Pulled =>
          logger.error(s"Notification is unpulled for id $id", headers.headers)
          errorBadRequest(badRequestUnpulledText).XmlResult
      }
    }

    val headers = request.headers
    logger.info(s"getting $notificationStatus notification id ${id.toString}", headers.headers)
    validateHeader(request.headers, s"get $notificationStatus") match {
      case Left(errorResponse) => Future.successful(errorResponse.XmlResult)
      case Right(clientId) =>
        queueService.get(clientId, id).map(opt =>
          opt.fold {
            logger.error(s"Notification not found for id ${id.toString}", headers.headers)
            ErrorNotFound.XmlResult
          } { n => datePulledResult(n, clientId, headers) }
        )
    }
  }

  private def result(n: Notification) =
    Result(
      header = ResponseHeader(OK, Map(LOCATION -> routes.QueueController.get(n.notificationId).url) ++ n.headers),
      body = HttpEntity.Strict(ByteString(n.payload), n.headers.get(CONTENT_TYPE))
    )

  private def validateHeader(headers: Headers, endpointName: String): Either[ErrorResponse, String] = {
    headers.get(X_CLIENT_ID_HEADER_NAME).fold[Either[ErrorResponse, String]]{
      logger.error(s"missing $X_CLIENT_ID_HEADER_NAME header when calling $endpointName endpoint", headers.headers)
      Left(errorBadRequest(MISSING_CLIENT_ID_ERROR))
    } { clientId =>
      Right(clientId)
    }
  }

}

@Singleton
class DateTimeProvider {
  def now(): DateTime = DateTime.now(UTC)
}
