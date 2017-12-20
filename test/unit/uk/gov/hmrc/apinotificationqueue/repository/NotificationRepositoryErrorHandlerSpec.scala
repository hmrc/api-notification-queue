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

import org.scalatest.mockito.MockitoSugar
import reactivemongo.api.commands.{DefaultWriteResult, WriteConcernError, WriteError}
import uk.gov.hmrc.play.test.UnitSpec

class NotificationRepositoryErrorHandlerSpec extends UnitSpec with MockitoSugar {

  private val clientId = "clientId"
  private val errorHandler = new NotificationRepositoryErrorHandler {}
  private val notification = mock[Notification]

  "NotificationRepositoryErrorHandler" should {

    "return notification if there are no database errors and at least one record inserted" in {
      val successfulWriteResult = writeResult(alteredRecords = 1)

      errorHandler.handleError(clientId, successfulWriteResult, notification) shouldBe notification
    }

    "throw a RuntimeException if there are no database errors but no record inserted" in {
      val noRecordsWriteResult = writeResult(alteredRecords = 0)

      val caught = intercept[RuntimeException](errorHandler.handleError(clientId, noRecordsWriteResult, notification))

      caught.getMessage shouldBe "Notification not inserted for client clientId"
    }

    "throw a RuntimeException if there is a database error" in {
      val writeConcernError = Some(WriteConcernError(1, "ERROR"))
      val errorWriteResult = writeResult(alteredRecords = 0, writeConcernError = writeConcernError)

      val caught = intercept[RuntimeException](errorHandler.handleError(clientId, errorWriteResult, notification))

      caught.getMessage shouldBe "Error inserting notification for clientId clientId : ERROR"
    }

  }


  private def writeResult(alteredRecords: Int, writeErrors: Seq[WriteError] = Nil,
                          writeConcernError: Option[WriteConcernError] = None) = {
    DefaultWriteResult(
      ok = true,
      n = alteredRecords,
      writeErrors = writeErrors,
      writeConcernError = writeConcernError,
      code = None,
      errmsg = None)
  }

}
