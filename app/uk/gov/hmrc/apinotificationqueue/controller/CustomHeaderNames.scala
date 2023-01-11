/*
 * Copyright 2023 HM Revenue & Customs
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

import uk.gov.hmrc.apinotificationqueue.controller.CustomHeaderNames._
import uk.gov.hmrc.customs.api.common.controllers.ErrorResponse
import uk.gov.hmrc.customs.api.common.controllers.ErrorResponse.errorBadRequest

object CustomHeaderNames {
  val X_CLIENT_ID_HEADER_NAME = "X-Client-ID"

  val X_CONVERSATION_ID_HEADER_NAME = "X-Conversation-ID"

  val API_SUBSCRIPTION_FIELDS_ID_HEADER_NAME = "api-subscription-fields-id"

  val NOTIFICATION_ID_HEADER_NAME: String = "notification-id"
}

object CustomErrorResponses {
  val ErrorClientIdMissing: ErrorResponse = errorBadRequest(s"$X_CLIENT_ID_HEADER_NAME required")

  val ErrorBodyMissing: ErrorResponse = errorBadRequest("Body required.")
}

