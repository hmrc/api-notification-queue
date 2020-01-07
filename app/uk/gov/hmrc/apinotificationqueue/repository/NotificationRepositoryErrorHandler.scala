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

package uk.gov.hmrc.apinotificationqueue.repository

import javax.inject.{Inject, Singleton}
import reactivemongo.api.commands.FindAndModifyCommand.Result
import reactivemongo.api.commands.WriteResult
import reactivemongo.play.json.JSONSerializationPack
import uk.gov.hmrc.apinotificationqueue.model.Notification
import uk.gov.hmrc.customs.api.common.logging.CdsLogger

@Singleton
class NotificationRepositoryErrorHandler @Inject() (cdsLogger: CdsLogger) {

  def handleDeleteError(result: WriteResult, exceptionMsg: String): Boolean = {
    handleError(result, databaseAltered, exceptionMsg)
  }

  def handleSaveError(writeResult: WriteResult, exceptionMsg: String, notification: Notification): Notification = {

    def handleSaveError(result: WriteResult): Notification =
      if (databaseAltered(result)) {
        notification
      }
      else {
        throw new RuntimeException(exceptionMsg)
      }

    handleError(writeResult, handleSaveError, exceptionMsg)
  }

  private def handleError[T](result: WriteResult, f: WriteResult => T, exceptionMsg: => String): T = {
    result.writeConcernError.fold(f(result)) {
      errMsg => {
        val errorMsg = s"$exceptionMsg. $errMsg"
        cdsLogger.error(errorMsg)
        throw new RuntimeException(errorMsg)
      }
    }
  }

  def handleUpdateError(result: Result[JSONSerializationPack.type], exceptionMsg: String, notification: Notification): Notification = {
    result.lastError.fold(notification) { lastError =>
      if (lastError.n > 0) {
        notification
      } else {
        val errorMsg = lastError.err.fold(exceptionMsg)(errMsg => s"$exceptionMsg. $errMsg")
        cdsLogger.error(errorMsg)
        throw new RuntimeException(errorMsg)
      }
    }
  }

  private def databaseAltered(writeResult: WriteResult): Boolean = writeResult.n > 0

}
