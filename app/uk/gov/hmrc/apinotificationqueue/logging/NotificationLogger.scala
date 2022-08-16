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

import com.google.inject.Inject
import javax.inject.Singleton
import uk.gov.hmrc.apinotificationqueue.model.SeqOfHeader
import uk.gov.hmrc.apinotificationqueue.logging.LoggingHelper.formatWithHeaders
import uk.gov.hmrc.customs.api.common.logging.CdsLogger

@Singleton
class NotificationLogger @Inject()(logger: CdsLogger) {

  def debug(msg: => String, headers: => SeqOfHeader): Unit = logger.debug(formatWithHeaders(msg, headers))
  def info(msg: => String, headers: => SeqOfHeader): Unit = logger.info(formatWithHeaders(msg, headers))
  def error(msg: => String, headers: => SeqOfHeader): Unit = logger.error(formatWithHeaders(msg, headers))

}
