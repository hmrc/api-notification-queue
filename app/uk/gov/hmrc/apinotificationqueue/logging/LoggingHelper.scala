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

package uk.gov.hmrc.apinotificationqueue.logging

import uk.gov.hmrc.apinotificationqueue.model.SeqOfHeader

object LoggingHelper {

  def formatWithHeaders(msg: String, headers: SeqOfHeader): String = {
    s"${formatLogPrefixWithHeaders(headers)} $msg\nheaders=$headers"
  }

  private def formatLogPrefixWithHeaders(headers: SeqOfHeader): String = {
    val maybeClientId = findHeaderValue("X-Client-ID", headers)
    val maybeConversationId = findHeaderValue("X-Conversation-ID", headers)

    maybeClientId.fold("")(maybeClientId => s"[clientId=$maybeClientId]") +
      maybeConversationId.fold("")(maybeConversationId => s"[conversationId=$maybeConversationId]")
  }

  private def findHeaderValue(headerName: String, headers: SeqOfHeader): Option[String] = {
    headers.collectFirst{
        case header if header._1.equalsIgnoreCase(headerName) => header._2
    }
  }
}
