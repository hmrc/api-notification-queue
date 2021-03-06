/*
 * Copyright 2021 HM Revenue & Customs
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

package unit.repository

import org.scalatestplus.mockito.MockitoSugar
import play.api.libs.json.Json
import reactivemongo.api.commands.FindAndModifyCommand.UpdateLastError
import reactivemongo.api.commands._
import reactivemongo.play.json.JSONSerializationPack
import uk.gov.hmrc.apinotificationqueue.model.Notification
import uk.gov.hmrc.apinotificationqueue.repository.NotificationRepositoryErrorHandler
import uk.gov.hmrc.customs.api.common.logging.CdsLogger
import util.UnitSpec
import util.TestData._

class NotificationRepositoryErrorHandlerSpec extends UnitSpec with MockitoSugar {

  private val mockCdsLogger = mock[CdsLogger]
  private val errorHandler = new NotificationRepositoryErrorHandler(mockCdsLogger)
  private val notification = mock[Notification]

  "NotificationRepositoryErrorHandler" can {

    "handle save" should {

      "return notification if there are no database errors and at least one record inserted" in {
        val successfulWriteResult = writeResult(alteredRecords = 1)

        errorHandler.handleSaveError(successfulWriteResult, "ERROR_MSG", notification) shouldBe notification
      }

      "throw a RuntimeException if there are no database errors but no record inserted" in {
        val noRecordsWriteResult = writeResult(alteredRecords = 0)

        val caught = intercept[RuntimeException](errorHandler.handleSaveError(noRecordsWriteResult, "ERROR_MSG", notification))

        caught.getMessage shouldBe "ERROR_MSG"
      }

      "throw a RuntimeException if there is a database error" in {
        val writeConcernError = Some(WriteConcernError(1, "ERROR"))
        val errorWriteResult = writeResult(alteredRecords = 0, writeConcernError = writeConcernError)

        val caught = intercept[RuntimeException](errorHandler.handleSaveError(errorWriteResult, "ERROR_MSG", notification))

        caught.getMessage shouldBe "ERROR_MSG. WriteConcernError(1,ERROR)"
      }
    }

    "handle update" should {

      "return notification if there are no database errors and at least one record inserted" in {
        val lastError = UpdateLastError(updatedExisting = true, upsertedId = Some(1), n = 1, err = None)

        val successfulUpdateResult = findAndModifyResult(lastError)

        errorHandler.handleUpdateError(successfulUpdateResult, "ERROR_MSG", notification) shouldBe notification
      }

      "throw a RuntimeException if there are no database errors but no record inserted" in {
        val lastError = UpdateLastError(updatedExisting = false, upsertedId = None, n = 0, err = None)
        val noRecordsUpdateResult = findAndModifyResult(lastError)

        val caught = intercept[RuntimeException](errorHandler.handleUpdateError(noRecordsUpdateResult, "ERROR_MSG", notification))

        caught.getMessage shouldBe "ERROR_MSG"
      }

      "throw a RuntimeException if there is a database error" in {
        val lastError = UpdateLastError(updatedExisting = false, upsertedId = None, n = 0, err = Some("database error"))
        val errorUpdateResult = findAndModifyResult(lastError)

        val caught = intercept[RuntimeException](errorHandler.handleUpdateError(errorUpdateResult, "ERROR_MSG", notification))

        caught.getMessage shouldBe "ERROR_MSG. database error"
      }
    }

    "handle Delete" should {
      "return true if there are no database errors and at least one record deleted" in {
        val successfulWriteResult = writeResult(alteredRecords = 1)

        errorHandler.handleDeleteError(successfulWriteResult, "ERROR_MSG") shouldBe true
      }

      "return false if there are no database errors and no record deleted" in {
        val noDeletedRecordsWriteResult = writeResult(alteredRecords = 0)

        errorHandler.handleDeleteError(noDeletedRecordsWriteResult, "ERROR_MSG") shouldBe false
      }

      "throw a RuntimeException if there is a database error" in {
        val writeConcernError = Some(WriteConcernError(1, "ERROR"))
        val errorWriteResult = writeResult(alteredRecords = 0, writeConcernError = writeConcernError)

        val caught = intercept[RuntimeException](errorHandler.handleDeleteError(errorWriteResult, "ERROR_MSG"))

        caught.getMessage shouldBe "ERROR_MSG. WriteConcernError(1,ERROR)"
      }
    }
  }

  private def writeResult(alteredRecords: Int, writeErrors: Seq[WriteError] = Nil,
                          writeConcernError: Option[WriteConcernError] = None): WriteResult = {
    UpdateWriteResult(
      ok = true,
      n = alteredRecords,
      writeErrors = writeErrors,
      writeConcernError = writeConcernError,
      code = None,
      errmsg = None,
      nModified = 1,
      upserted = Seq.empty
    )
  }

  private def findAndModifyResult(lstError: UpdateLastError): FindAndModifyCommand.Result[JSONSerializationPack.type] = {
    new FindAndModifyCommand.Result[JSONSerializationPack.type]{
      val pack = JSONSerializationPack
      def lastError = Some(lstError)
      def value = Json.toJson(Notification1).asOpt[JSONSerializationPack.Document]
    }
  }
}
