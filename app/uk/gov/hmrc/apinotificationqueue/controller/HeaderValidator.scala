/*
 * Copyright 2022 HM Revenue & Customs
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

import play.api.mvc.Headers
import uk.gov.hmrc.apinotificationqueue.controller.CustomErrorResponses.ErrorClientIdMissing
import uk.gov.hmrc.apinotificationqueue.controller.CustomHeaderNames.{API_SUBSCRIPTION_FIELDS_ID_HEADER_NAME, X_CLIENT_ID_HEADER_NAME}
import uk.gov.hmrc.apinotificationqueue.logging.NotificationLogger
import uk.gov.hmrc.customs.api.common.controllers.ErrorResponse

import scala.util.{Failure, Success, Try}

trait HeaderValidator {

  val notificationLogger: NotificationLogger

  def validateClientIdHeader(headers: Headers, endpointName: String): Either[ErrorResponse, String] = {
    validateHeader(headers, endpointName, X_CLIENT_ID_HEADER_NAME)
  }

  def validateApiSubscriptionFieldsHeader(headers: Headers, endpointName: String): Either[ErrorResponse, String] = {
    validateHeader(headers, endpointName, API_SUBSCRIPTION_FIELDS_ID_HEADER_NAME) match {
      case Left(errorResponse) => Left(errorResponse)
      case Right(fieldsId) =>
        val maybeUuid = validateUuid(fieldsId)
        if (maybeUuid.isDefined) {
          Right(maybeUuid.get.toString)
        }
        else {
          notificationLogger.error(s"invalid $API_SUBSCRIPTION_FIELDS_ID_HEADER_NAME NOT-A_UUID", headers.headers)
          Left(ErrorClientIdMissing)
        }
    }
  }

  private def validateHeader(headers: Headers, endpointName: String, headerName: String): Either[ErrorResponse, String] = {
    headers.get(headerName).fold[Either[ErrorResponse, String]]{
      notificationLogger.error(s"missing $headerName header when calling $endpointName endpoint", headers.headers)
      Left(ErrorClientIdMissing)
    } { clientId =>
      Right(clientId)
    }
  }

  def validateUuid(maybeUuid: String): Option[UUID] = Try(UUID.fromString(maybeUuid)) match {
    case Success(uuid) => Some(uuid)
    case Failure(_) => None
  }

}
