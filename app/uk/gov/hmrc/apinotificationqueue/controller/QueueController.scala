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

package uk.gov.hmrc.apinotificationqueue.controller

import java.util.UUID
import javax.inject.{Inject, Singleton}

import akka.util.ByteString
import org.joda.time.DateTime
import play.api.http.HttpEntity
import play.api.libs.json.Json
import play.api.mvc._
import uk.gov.hmrc.apinotificationqueue.model.{Notification, Notifications}
import uk.gov.hmrc.apinotificationqueue.service.{ApiSubscriptionFieldsService, QueueService}
import uk.gov.hmrc.customs.api.common.logging.CdsLogger
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.bootstrap.controller.BaseController
import uk.gov.hmrc.play.http.logging.MdcLoggingExecutionContext._

import scala.concurrent.Future
import scala.util.{Failure, Success, Try}

@Singleton()
class QueueController @Inject()(queueService: QueueService,
                                fieldsService: ApiSubscriptionFieldsService,
                                idGenerator: NotificationIdGenerator,
                                cdsLogger: CdsLogger) extends BaseController {

  private val SUBSCRIPTION_FIELD_HEADER_NAME = "api-subscription-fields-id"
  private val CLIENT_ID_HEADER_NAME = "X-Client-ID"

  private val MISSING_CLIENT_ID_ERROR = s"$CLIENT_ID_HEADER_NAME required."
  private val MISSING_BODY_ERROR = s"Body required."

  def save(): Action[AnyContent] = Action.async {
    implicit request => {
      val headers = request.headers
      val message = headers.get("X-Conversation-ID").fold(s"[conversationId not found] Headers=$headers"){ cid => s"[conversationId=$cid]"}
      cdsLogger.debug(s"saving request - $message")
      getClientId(headers).flatMap(_.fold(Future.successful(BadRequest(MISSING_CLIENT_ID_ERROR))) {
        clientId =>
          request.body.asXml.fold(Future.successful(BadRequest(MISSING_BODY_ERROR))) { body =>
            queueService.save(
              clientId,
              Notification(
                idGenerator.generateId(),
                headers.remove(CLIENT_ID_HEADER_NAME, SUBSCRIPTION_FIELD_HEADER_NAME).toSimpleMap,
                body.toString(),
                DateTime.now()
              )
            ).map(notification =>
              Result(ResponseHeader(CREATED, Map(LOCATION -> routes.QueueController.get(notification.notificationId).url)), HttpEntity.NoEntity))
          }

      })
    }
  }

  private def getClientId(headers: Headers)(implicit hc: HeaderCarrier): Future[Option[String]] = {

    def getClientIdFromSubId(headers: Headers): Future[Option[String]] = {
      val noResponse: Future[Option[String]] = Future.successful(None)
      Try(headers.get(SUBSCRIPTION_FIELD_HEADER_NAME).fold(noResponse)(id => fieldsService.getClientId(UUID.fromString(id)))) match {
        case Success(v) => v
        case Failure(_) => noResponse
      }
    }

    headers.get(CLIENT_ID_HEADER_NAME).fold(getClientIdFromSubId(headers))(id => Future.successful(Some(id)))
  }

  def getAllByClientId: Action[AnyContent] = Action.async {
    implicit request =>
      request.headers.get(CLIENT_ID_HEADER_NAME).fold(Future.successful(BadRequest(MISSING_CLIENT_ID_ERROR))) { clientId =>

        val notificationIdPaths: Future[List[String]] = for {
          notifications <- queueService.get(clientId)
        } yield notifications.map("/notification/" + _.notificationId)

        notificationIdPaths.map(idPaths => Ok(Json.toJson(Notifications(idPaths))))
      }
  }

  def get(id: UUID): Action[AnyContent] = Action.async { implicit request =>
    request.headers.get(CLIENT_ID_HEADER_NAME).fold(Future.successful(BadRequest(MISSING_CLIENT_ID_ERROR))) { clientId =>
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
    request.headers.get(CLIENT_ID_HEADER_NAME).fold(Future.successful(BadRequest(MISSING_CLIENT_ID_ERROR))) { clientId =>
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
