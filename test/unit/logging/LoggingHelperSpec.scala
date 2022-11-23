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

package unit.logging

import uk.gov.hmrc.apinotificationqueue.logging.LoggingHelper
import util.RequestHeaders.LoggingHeaders
import util.UnitSpec

class LoggingHelperSpec extends UnitSpec {

  val debugMsg = "DEBUG"

  "LoggingHelper" should {

    "format with headers" in {
      val actual = LoggingHelper.formatWithHeaders(debugMsg, LoggingHeaders)

      actual shouldBe "[clientId=clientId1][conversationId=eaca01f9-ec3b-4ede-b263-61b626dde231] DEBUG\nheaders=List((X-Client-ID,clientId1), (X-Conversation-ID,eaca01f9-ec3b-4ede-b263-61b626dde231))"
    }

    "format without headers" in {
      val actual = LoggingHelper.formatWithHeaders(debugMsg, Seq.empty)

      actual shouldBe " DEBUG\nheaders=List()"
    }

  }
}
