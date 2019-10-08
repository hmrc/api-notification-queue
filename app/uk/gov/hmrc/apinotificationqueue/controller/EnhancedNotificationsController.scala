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
import play.api.libs.json.Json
import play.api.mvc._
import uk.gov.hmrc.apinotificationqueue.logging.NotificationLogger
import uk.gov.hmrc.apinotificationqueue.model.NotificationStatus._
import uk.gov.hmrc.apinotificationqueue.model.{Notification, NotificationStatus, Notifications}
import uk.gov.hmrc.apinotificationqueue.service.{ApiSubscriptionFieldsService, QueueService}
import uk.gov.hmrc.customs.api.common.controllers.ErrorResponse.{ErrorNotFound, errorBadRequest}
import uk.gov.hmrc.play.bootstrap.controller.BackendController

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class EnhancedNotificationsController @Inject()(queueService: QueueService,
                                                fieldsService: ApiSubscriptionFieldsService,
                                                idGenerator: NotificationIdGenerator,
                                                dateTimeProvider: DateTimeProvider,
                                                cc: ControllerComponents,
                                                logger: NotificationLogger)
                                               (implicit ec: ExecutionContext)
  extends BackendController(cc)
    with HeaderValidator {

  override val notificationLogger: NotificationLogger = logger
  private val badRequestPulledText = "Notification has been pulled"
  private val badRequestUnpulledText = "Notification is unpulled"

  def getPulledByNotificationId(notificationId: UUID): Action[AnyContent] = get(notificationId, Pulled)
  def getUnpulledByNotificationId(notificationId: UUID): Action[AnyContent] = get(notificationId, Unpulled)

  def getAllPulledByClientId: Action[AnyContent] = getAllByClientId(Pulled)
  def getAllUnpulledByClientId: Action[AnyContent] = getAllByClientId(Unpulled)

  def getAllPulledByConversationId(conversationId: UUID): Action[AnyContent] = getAllByConversationId(conversationId: UUID, Pulled)
  def getAllUnpulledByConversationId(conversationId: UUID): Action[AnyContent] = getAllByConversationId(conversationId: UUID, Unpulled)

  def getAllByConversationId(conversationId: UUID): Action[AnyContent] = Action.async { implicit request =>

    val headers = request.headers
    logger.info(s"listing notifications with conversationId $conversationId", headers.headers)
    validateClientIdHeader(request.headers, "getAllByConversationId") match {
      case Left(errorResponse) => Future.successful(errorResponse.XmlResult)
      case Right(clientId) =>
        val notificationIdPaths: Future[List[String]] =
          for {
            notificationIds <- queueService.getByConversationId(clientId, conversationId)
          } yield notificationIds.map { n =>
            val status = if (n.pulled) "pulled" else "unpulled"
            s"/notifications/$status/${n.notification.notificationId.toString}"
          }

        generateResponse(notificationIdPaths, headers)
    }
  }
  
  private def getAllByClientId(notificationStatus: NotificationStatus.Value): Action[AnyContent] = Action.async { implicit request =>

    val headers: Headers = request.headers
    logger.info(s"listing $notificationStatus notifications by clientId", headers.headers)
    validateClientIdHeader(request.headers, s"get all $notificationStatus by client id") match {
      case Left(errorResponse) => Future.successful(errorResponse.XmlResult)
      case Right(clientId) =>
        val notificationIdPaths: Future[List[String]] =
          for {
            notificationIds <- queueService.get(clientId, Some(notificationStatus))
          } yield notificationIds.map(s"/notifications/$notificationStatus/" + _.notification.notificationId.toString)

        generateResponse(notificationIdPaths, headers)
    }
  }

  private def getAllByConversationId(conversationId: UUID, notificationStatus: NotificationStatus.Value): Action[AnyContent] = Action.async { implicit request =>

    val headers: Headers = request.headers
    logger.info(s"listing $notificationStatus notifications by conversationId ${conversationId.toString}", headers.headers)
    validateClientIdHeader(request.headers, s"get all $notificationStatus by conversation id") match {
      case Left(errorResponse) => Future.successful(errorResponse.XmlResult)
      case Right(clientId) =>
        val notificationIdPaths: Future[List[String]] =
          for {
            notificationIds <- queueService.getByConversationId(clientId, conversationId: UUID, notificationStatus)
          } yield notificationIds.map(s"/notifications/$notificationStatus/" + _.notification.notificationId.toString)

        generateResponse(notificationIdPaths, headers)
    }
  }

  private def generateResponse(notificationIdPaths: Future[List[String]], headers: Headers) = {
    notificationIdPaths.map { idPaths =>
      val json = Json.toJson(Notifications(idPaths))
      logger.debug(s"returning notifications $json", headers.headers)
      Ok(json)
    }
  }

  private def get(notificationId: UUID, notificationStatus: NotificationStatus.Value): Action[AnyContent] = Action.async { implicit request =>

    def datePulledResult(n: Notification, clientId: String, headers: Headers) = {
      n.datePulled match {
        case Some(_) if notificationStatus == Unpulled =>
          logger.error(s"Notification has been pulled for id ${notificationId.toString}", headers.headers)
          errorBadRequest(badRequestPulledText).XmlResult
        case Some(_) if notificationStatus == Pulled =>
          val conversationId = n.headers.getOrElse("X-Conversation-ID", "")
          logger.debug(s"Pulling pulled notification for conversationId: ${conversationId.toString} with notificationId: ${notificationId.toString}", headers.headers)
          result(n)
        case None if notificationStatus == Unpulled =>
          val conversationId = n.headers.getOrElse("X-Conversation-ID", "")
          logger.debug(s"Pulling unpulled notification for conversationId: ${conversationId} with notificationId: ${notificationId.toString}", headers.headers)
          queueService.update(clientId, n.copy(datePulled = Some(dateTimeProvider.now())))
          result(n)
        case None if notificationStatus == Pulled =>
          logger.error(s"Notification is unpulled for id $notificationId", headers.headers)
          errorBadRequest(badRequestUnpulledText).XmlResult
      }
    }

    val headers = request.headers
    logger.info(s"getting $notificationStatus notificationId ${notificationId.toString}", headers.headers)
    validateClientIdHeader(request.headers, s"get $notificationStatus") match {
      case Left(errorResponse) => Future.successful(errorResponse.XmlResult)
      case Right(clientId) =>
        queueService.get(clientId, notificationId).map(opt =>
          opt.fold {
            logger.error(s"Notification not found for id ${notificationId.toString}", headers.headers)
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

}

@Singleton
class DateTimeProvider {
  def now(): DateTime = DateTime.now(UTC)
}
