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
import play.api.http.HttpEntity
import play.api.libs.concurrent.Execution.Implicits.defaultContext
import play.api.libs.json.Json
import play.api.mvc._
import uk.gov.hmrc.apinotificationqueue.logging.NotificationLogger
import uk.gov.hmrc.apinotificationqueue.model.{Notification, Notifications}
import uk.gov.hmrc.apinotificationqueue.service.{ApiSubscriptionFieldsService, QueueService}
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.bootstrap.controller.BaseController
import uk.gov.hmrc.apinotificationqueue.controller.CustomHeaderNames.{X_CLIENT_ID_HEADER_NAME, API_SUBSCRIPTION_FIELDS_ID_HEADER_NAME}

import scala.concurrent.Future
import scala.util.control.NonFatal
import scala.util.{Failure, Success, Try}

//TODO: there needs to be a separate validation stage before main processing. At the moment validation responsibilities are sprayed throughout the code
@Singleton()
class QueueController @Inject()(queueService: QueueService,
                                fieldsService: ApiSubscriptionFieldsService,
                                idGenerator: NotificationIdGenerator,
                                dateTimeProvider: DateTimeProvider,
                                logger: NotificationLogger) extends BaseController {

  private val MISSING_CLIENT_ID_ERROR = s"$X_CLIENT_ID_HEADER_NAME required"
  private val MISSING_BODY_ERROR = "Body required."

  def save(): Action[AnyContent] = Action.async {
    implicit request => {
      val headers = request.headers
      logger.debug(s"saving request", headers.headers)
      getClientId(headers).flatMap(_.fold(Future.successful(BadRequest(MISSING_CLIENT_ID_ERROR))) {
        clientId =>
          request.body.asXml.fold(Future.successful(BadRequest(MISSING_BODY_ERROR))) { body =>
            queueService.save(
              clientId,
              Notification(
                idGenerator.generateId(),
                headers.remove(X_CLIENT_ID_HEADER_NAME, API_SUBSCRIPTION_FIELDS_ID_HEADER_NAME).toSimpleMap,
                body.toString(),
                dateTimeProvider.now(),
                None
              )
            ).map(notification =>
              Result(ResponseHeader(CREATED, Map(LOCATION -> routes.QueueController.get(notification.notificationId).url)), HttpEntity.NoEntity))
          }

      })
    }
  }

  private def getClientId(headers: Headers)(implicit hc: HeaderCarrier): Future[Option[String]] = {

    def maybeUuid(subscriptionFieldsId: String): Option[UUID] = Try(UUID.fromString(subscriptionFieldsId)) match {
      case Success(uuid) => Some(uuid)
      case Failure(_) => None
    }

    def getClientIdFromSubId(headers: Headers): Future[Option[String]] = {

      val noResponse: Future[Option[String]] = Future.successful(None)
      headers.get(API_SUBSCRIPTION_FIELDS_ID_HEADER_NAME).fold(noResponse){ subscriptionFieldsId =>
        maybeUuid(subscriptionFieldsId).fold{
          logger.error(s"invalid subscriptionFieldsId $subscriptionFieldsId", headers.headers)
          noResponse
        }
        { uuid =>
          fieldsService.getClientId(uuid).recoverWith{
            case NonFatal(e) =>
              logger.error(s"Error calling subscription fields id due to ${e.getMessage}", headers.headers)
              noResponse
          }
        }
      }
    }


    headers.get(X_CLIENT_ID_HEADER_NAME).fold(getClientIdFromSubId(headers))(clientId => Future.successful(Some(clientId)))
  }

  def getAllByClientId: Action[AnyContent] = Action.async {
    implicit request =>
      request.headers.get(X_CLIENT_ID_HEADER_NAME).fold(Future.successful(BadRequest(MISSING_CLIENT_ID_ERROR))) { clientId =>

        val notificationIdPaths: Future[List[String]] = for {
          notificationIds <- queueService.get(clientId, None)
        } yield notificationIds.map("/notification/" + _.notification.notificationId.toString)

        notificationIdPaths.map(idPaths => Ok(Json.toJson(Notifications(idPaths))))
      }
  }

  def get(id: UUID): Action[AnyContent] = Action.async { implicit request =>
    request.headers.get(X_CLIENT_ID_HEADER_NAME).fold(Future.successful(BadRequest(MISSING_CLIENT_ID_ERROR))) { clientId =>
      val notification = queueService.get(clientId, id)
      notification.map(opt =>
        opt.fold(NotFound("NOT FOUND"))(
          n => Result(
            header = ResponseHeader(OK, Map(LOCATION -> routes.QueueController.get(id).url) ++ n.headers),
            body = HttpEntity.Strict(ByteString(n.payload), n.headers.get(CONTENT_TYPE))
          )
        )
      )
    }
  }

  def delete(id: UUID): Action[AnyContent] = Action.async { implicit request =>
    request.headers.get(X_CLIENT_ID_HEADER_NAME).fold(Future.successful(BadRequest(MISSING_CLIENT_ID_ERROR))) { clientId =>
      val futureDeleted = queueService.delete(clientId, id)
      futureDeleted.map(deleted =>
        if (deleted) {
          NoContent
        } else {
          NotFound("NOT FOUND")
        }
      )
    }
  }

}

@Singleton
class NotificationIdGenerator {
  def generateId(): UUID = {
    UUID.randomUUID()
  }
}
