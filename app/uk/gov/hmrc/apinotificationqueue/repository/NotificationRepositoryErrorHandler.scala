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

package uk.gov.hmrc.apinotificationqueue.repository

import play.api.Logger
import reactivemongo.api.commands.WriteResult

trait NotificationRepositoryErrorHandler {

  def handleError(clientId: String, writeResult: WriteResult, notification: Notification): Notification = {
    lazy val recordNotInsertedError = s"Notification not inserted for client $clientId"

    writeResult.writeConcernError.fold(databaseAltered(writeResult, notification, recordNotInsertedError)){ writeConcernError =>
      val dbErrMsg = s"Error inserting notification for clientId $clientId : ${writeConcernError.errmsg}"
      Logger.error(dbErrMsg)
      throw new RuntimeException(dbErrMsg)
    }
  }

  private def databaseAltered(writeResult: WriteResult, notification: Notification, errMsg: => String): Notification = {
    if (writeResult.n > 0) {
      notification
    } else {
      throw new RuntimeException(errMsg)
    }
  }

}
