/*
 * Copyright 2017 HM Revenue & Customs
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
import play.api.mvc._
import uk.gov.hmrc.apinotificationqueue.repository.Notification
import uk.gov.hmrc.apinotificationqueue.service.QueueService
import uk.gov.hmrc.http.BadRequestException
import uk.gov.hmrc.play.bootstrap.controller.BaseController
import uk.gov.hmrc.play.http.logging.MdcLoggingExecutionContext._

import scala.concurrent.Future

@Singleton()
class QueueController @Inject()(queueService: QueueService, idGenerator: NotificationIdGenerator) extends BaseController {
  val CLIENT_ID_HEADER_NAME = "x-client-id"

  def save() = Action.async {
    implicit request => {
      val headers = request.headers
      val clientId = headers.get(CLIENT_ID_HEADER_NAME).getOrElse(throw new BadRequestException("x-client-id required"))
      val notificationId = idGenerator.generateId()
      queueService.save(
        clientId,
        Notification(
          notificationId,
          headers.remove(CLIENT_ID_HEADER_NAME).toSimpleMap,
          request.body.asXml.getOrElse(throw new BadRequestException("no body included")).toString(),
          DateTime.now()
        )
      )
      Future.successful(Result(ResponseHeader(CREATED, Map(LOCATION -> routes.QueueController.get(notificationId).url)), HttpEntity.NoEntity))
    }
  }

  def getAll = Action.async {

    Future.successful(Result(ResponseHeader(OK), HttpEntity.NoEntity))
  }

  def get(id: UUID) = Action.async {
    implicit request => {
      val headers = request.headers
      val clientId = headers.get(CLIENT_ID_HEADER_NAME).getOrElse(throw new BadRequestException("x-client-id required"))
      val notification = queueService.get(clientId, id)
      notification.map(opt =>
        opt.fold(NotFound("NOT FOUND"))(
          n => Result(
            ResponseHeader(OK, Map(LOCATION -> routes.QueueController.get(id).url) ++ n.headers),
            HttpEntity.Strict(ByteString(n.payload), n.headers.get(CONTENT_TYPE))
          )
        )
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
