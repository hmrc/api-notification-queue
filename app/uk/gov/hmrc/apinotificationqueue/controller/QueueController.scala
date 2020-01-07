/*
 * Copyright 2020 HM Revenue & Customs
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
import play.api.http.HttpEntity
import play.api.libs.json.Json
import play.api.mvc._
import uk.gov.hmrc.apinotificationqueue.controller.CustomErrorResponses.{ErrorBodyMissing, ErrorClientIdMissing}
import uk.gov.hmrc.apinotificationqueue.controller.CustomHeaderNames.{API_SUBSCRIPTION_FIELDS_ID_HEADER_NAME, NOTIFICATION_ID_HEADER_NAME, X_CLIENT_ID_HEADER_NAME}
import uk.gov.hmrc.apinotificationqueue.logging.NotificationLogger
import uk.gov.hmrc.apinotificationqueue.model.{Notification, Notifications}
import uk.gov.hmrc.apinotificationqueue.service.{ApiSubscriptionFieldsService, QueueService, UuidService}
import uk.gov.hmrc.play.bootstrap.controller.BackendController

import scala.concurrent.{ExecutionContext, Future}
import scala.util.control.NonFatal

//TODO: there needs to be a separate validation stage before main processing. At the moment validation responsibilities are sprayed throughout the code
@Singleton()
class QueueController @Inject()(queueService: QueueService,
                                fieldsService: ApiSubscriptionFieldsService,
                                uuidService: UuidService,
                                dateTimeProvider: DateTimeProvider,
                                cc: ControllerComponents,
                                logger: NotificationLogger)
                               (implicit ec: ExecutionContext)
  extends BackendController(cc)
    with HeaderValidator {

  override val notificationLogger: NotificationLogger = logger

  def save(): Action[AnyContent] = Action.async { implicit request =>
    val headers: Headers = request.headers
    logger.info(s"saving request", headers.headers)
    validateApiSubscriptionFieldsHeader(headers, "save") match {
      case Left(errorResponse) => Future.successful(errorResponse.XmlResult)
      case Right(fieldsId) => fieldsService.getClientId(UUID.fromString(fieldsId)).flatMap { maybeClientId =>
        if (maybeClientId.isEmpty) {
          logger.error(s"unable to retrieve clientId from api-subscription-fields service for fieldsId $fieldsId", headers.headers)
          Future.successful(ErrorClientIdMissing.XmlResult)
        }
        else {
          request.body.asXml.fold {
            logger.error("missing body when saving", headers.headers)
            Future.successful(ErrorBodyMissing.XmlResult)
          } { body =>
             val notificationId = extractNotificationIdHeaderValue(headers).getOrElse(uuidService.uuid())
              queueService.save(
                maybeClientId.get,
                Notification(
                  notificationId,
                  headers.remove(X_CLIENT_ID_HEADER_NAME, API_SUBSCRIPTION_FIELDS_ID_HEADER_NAME, NOTIFICATION_ID_HEADER_NAME).toSimpleMap,
                  body.toString(),
                  dateTimeProvider.now(),
                  None)).map { notification =>
                    Result(ResponseHeader(CREATED, Map(LOCATION -> routes.QueueController.get(notification.notificationId).url)), HttpEntity.NoEntity)
                  }
            }
        }
      }.recover {
        case NonFatal(e) =>
          logger.error(s"Error calling api-subscription-fields-service due to ${e.getMessage}", headers.headers)
          ErrorClientIdMissing.XmlResult
      }
    }
  }

  def getAllByClientId: Action[AnyContent] = Action.async { implicit request =>

    logger.info("getting all notifications", request.headers.headers)
    validateClientIdHeader(request.headers, "getAllByClientId") match {
      case Left(errorResponse) => Future.successful(errorResponse.XmlResult)
      case Right(clientId) =>
        val notificationIdPaths: Future[List[String]] = for {
          notificationIds <- queueService.get(clientId, None)
        } yield notificationIds.map("/notification/" + _.notification.notificationId.toString)

        notificationIdPaths.map { idPaths =>
          val json = Json.toJson(Notifications(idPaths))
          logger.debug(s"listing all notifications $json", request.headers.headers)
          Ok(json)
        }
    }
  }

  def get(notificationId: UUID): Action[AnyContent] = Action.async { implicit request =>

    logger.info(s"getting notification id ${notificationId.toString}", request.headers.headers)
    validateClientIdHeader(request.headers, "get") match {
      case Left(errorResponse) => Future.successful(errorResponse.XmlResult)
      case Right(clientId) =>
        val notification = queueService.get(clientId, notificationId)
        notification.map(opt =>
          opt.fold {
            logger.debug(s"requested notification id ${notificationId.toString} not found", request.headers.headers)
            NotFound("NOT FOUND")
          } {n =>
            logger.debug(s"found notification id ${notificationId.toString}", request.headers.headers)
              Result(
                header = ResponseHeader(OK, Map(LOCATION -> routes.QueueController.get(notificationId).url) ++ n.headers),
                body = HttpEntity.Strict(ByteString(n.payload), n.headers.get(CONTENT_TYPE)))
          }
        )
    }
  }

  def delete(notificationId: UUID): Action[AnyContent] = Action.async { implicit request =>

    logger.info(s"deleting notification id ${notificationId.toString}", request.headers.headers)
    validateClientIdHeader(request.headers, "delete") match {
      case Left(errorResponse) => Future.successful(errorResponse.XmlResult)
      case Right(clientId) =>
        val futureDeleted = queueService.delete(clientId, notificationId)
        futureDeleted.map(deleted =>
          if (deleted) {
            logger.debug(s"successfully deleted notification id ${notificationId.toString}", request.headers.headers)
            NoContent
          } else {
            logger.debug(s"nothing to delete for notification id ${notificationId.toString}", request.headers.headers)
            NotFound("NOT FOUND")
          }
        )
    }
  }

  private def extractNotificationIdHeaderValue(headers: Headers): Option[UUID] = {
    headers.get(NOTIFICATION_ID_HEADER_NAME).fold[Option[UUID]](None)(id => validateUuid(id))
  }

}
