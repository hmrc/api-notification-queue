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
import uk.gov.hmrc.apinotificationqueue.model.NotificationStatus._
import uk.gov.hmrc.apinotificationqueue.model.{Notification, NotificationStatus, Notifications}
import uk.gov.hmrc.apinotificationqueue.service.{ApiSubscriptionFieldsService, QueueService}
import uk.gov.hmrc.customs.api.common.controllers.ErrorResponse.{ErrorNotFound, errorBadRequest}
import uk.gov.hmrc.customs.api.common.logging.CdsLogger
import uk.gov.hmrc.play.bootstrap.controller.BaseController

import scala.concurrent.Future

@Singleton
class EnhancedNotificationsController @Inject()(queueService: QueueService,
                                                fieldsService: ApiSubscriptionFieldsService,
                                                idGenerator: NotificationIdGenerator,
                                                dateTimeProvider: DateTimeProvider,
                                                cdsLogger: CdsLogger) extends BaseController {

  private val CLIENT_ID_HEADER_NAME = "X-Client-ID"

  private val MISSING_CLIENT_ID_ERROR = s"$CLIENT_ID_HEADER_NAME required"

  private val badRequestPulledText = "Notification has been pulled"
  private val badRequestUnpulledText = "Notification is unpulled"

  def getPulledByClientId: Action[AnyContent] = pullByClientId(Pulled)
  def getUnpulledByClientId: Action[AnyContent] = pullByClientId(Unpulled)

  private def pullByClientId(notificationStatus: NotificationStatus.Value): Action[AnyContent] = Action.async {
    implicit request =>
      request.headers.get(CLIENT_ID_HEADER_NAME).fold(Future.successful(errorBadRequest(MISSING_CLIENT_ID_ERROR).XmlResult)) { clientId =>
        val notificationIdPaths: Future[List[String]] =
          for {
            notificationIds <- queueService.get(clientId, Some(notificationStatus))
          } yield notificationIds.map(s"/notifications/$notificationStatus/" + _.notification.notificationId.toString)

        notificationIdPaths.map(idPaths => Ok(Json.toJson(Notifications(idPaths))))
      }
  }

  def unpulled(id: UUID): Action[AnyContent] = pull(id, Unpulled)
  def pulled(id: UUID): Action[AnyContent] = pull(id, Pulled)

  private def pull(id: UUID, notificationStatus: NotificationStatus.Value): Action[AnyContent] = Action.async { implicit request =>
    request.headers.get(CLIENT_ID_HEADER_NAME).fold {
      cdsLogger.error("Client id is missing")
      Future.successful(errorBadRequest(MISSING_CLIENT_ID_ERROR).XmlResult)
    } { clientId =>
      val notification = queueService.get(clientId, id)
      notification.map(opt =>
        opt.fold {
          cdsLogger.error(s"Notification not found for id: ${id.toString}")
          ErrorNotFound.XmlResult
        } {
          n =>
            n.datePulled match {
              case Some(_) if notificationStatus == Unpulled =>
                cdsLogger.error(s"Notification has been pulled for id: ${id.toString}")
                errorBadRequest(badRequestPulledText).XmlResult
              case Some(_) if notificationStatus == Pulled =>
                cdsLogger.debug(s"Pulling pulled notification for id: ${id.toString}")
                result(n)
              case None if notificationStatus == Unpulled =>
                cdsLogger.debug(s"Pulling unpulled notification for id: ${id.toString}")
                queueService.update(clientId, n.copy(datePulled = Some(dateTimeProvider.now())))
                result(n)
              case None if notificationStatus == Pulled =>
                cdsLogger.error(s"Notification is unpulled for id: $id")
                errorBadRequest(badRequestUnpulledText).XmlResult
            }
        }
      )
    }
  }

  private def result(n: Notification) =
    Result(
      header = ResponseHeader(OK, Map(LOCATION -> routes.QueueController.get(n.notificationId).url) ++ n.headers),
      body = HttpEntity.Strict(ByteString(n.payload), n.headers.get(CONTENT_TYPE))
    )

}

@Singleton
class DateTimeProvider {
  def now(): DateTime = DateTime.now(UTC)
}
