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

import akka.util.ByteString
import javax.inject.{Inject, Singleton}
import play.api.http.HttpEntity
import play.api.mvc._
import uk.gov.hmrc.apinotificationqueue.service.{ApiSubscriptionFieldsService, QueueService}
import uk.gov.hmrc.customs.api.common.logging.CdsLogger
import uk.gov.hmrc.play.bootstrap.controller.BaseController
import uk.gov.hmrc.play.http.logging.MdcLoggingExecutionContext._

import scala.concurrent.Future

@Singleton()
class EnhancedNotificationsController @Inject()(queueService: QueueService,
                                                fieldsService: ApiSubscriptionFieldsService,
                                                idGenerator: NotificationIdGenerator,
                                                cdsLogger: CdsLogger) extends BaseController {

  private val CLIENT_ID_HEADER_NAME = "X-Client-ID"

  private val MISSING_CLIENT_ID_ERROR = s"$CLIENT_ID_HEADER_NAME required."

  //TODO MC move this to QueueController?
  def read(id: UUID): Action[AnyContent] = Action.async { implicit request =>
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

}
