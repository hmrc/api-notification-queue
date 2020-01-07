/*
 * Copyright 2020 HM Revenue & Customs
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

import org.scalatestplus.mockito.MockitoSugar
import uk.gov.hmrc.apinotificationqueue.logging.NotificationLogger
import uk.gov.hmrc.customs.api.common.logging.CdsLogger
import uk.gov.hmrc.play.test.UnitSpec
import util.MockitoPassByNameHelper.PassByNameVerifier
import util.RequestHeaders.LoggingHeaders

class NotificationLoggerSpec extends UnitSpec with MockitoSugar {

  trait SetUp {
    val mockCdsLogger: CdsLogger = mock[CdsLogger]
    val logger = new NotificationLogger(mockCdsLogger)
  }

  "NotificationsLogger" should {
    "debug(s: => String, headers: => SeqOfHeader)" in new SetUp {
      logger.debug("msg", LoggingHeaders)

      PassByNameVerifier(mockCdsLogger, "debug")
        .withByNameParam("[clientId=clientId1][conversationId=eaca01f9-ec3b-4ede-b263-61b626dde231] msg\nheaders=List((X-Client-ID,clientId1), (X-Conversation-ID,eaca01f9-ec3b-4ede-b263-61b626dde231))")
        .verify()
    }

    "info(s: => String, headers: => SeqOfHeader)" in new SetUp {
      logger.info("msg", LoggingHeaders)

      PassByNameVerifier(mockCdsLogger, "info")
        .withByNameParam("[clientId=clientId1][conversationId=eaca01f9-ec3b-4ede-b263-61b626dde231] msg\nheaders=List((X-Client-ID,clientId1), (X-Conversation-ID,eaca01f9-ec3b-4ede-b263-61b626dde231))")
        .verify()
    }

    "error(s: => String, headers: => SeqOfHeader)" in new SetUp {
      logger.error("msg", LoggingHeaders)

      PassByNameVerifier(mockCdsLogger, "error")
        .withByNameParam("[clientId=clientId1][conversationId=eaca01f9-ec3b-4ede-b263-61b626dde231] msg\nheaders=List((X-Client-ID,clientId1), (X-Conversation-ID,eaca01f9-ec3b-4ede-b263-61b626dde231))")
        .verify()
    }
  }
}
