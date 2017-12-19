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

package uk.gov.hmrc.apinotificationqueue.controller

import java.util.UUID

import org.joda.time.DateTime
import org.mockito.Mockito._
import org.scalatest.mockito.MockitoSugar
import uk.gov.hmrc.apinotificationqueue.repository.{Message, MessageRepository}
import uk.gov.hmrc.apinotificationqueue.service.QueueService
import uk.gov.hmrc.play.test.UnitSpec

class QueueServiceSpec extends UnitSpec with MockitoSugar {

  trait Setup {
    val mockRepo = mock[MessageRepository]
    val serviceUnderTest = new QueueService(mockRepo)

    val clientId = "clientId"
    val message = Message(UUID.randomUUID(), Map.empty, "<xml></xml>", DateTime.now())

  }


  "Save" should {
    "Save in the repo" in new Setup {
      serviceUnderTest.save(clientId, message)
      verify(mockRepo).save(clientId, message)
    }
  }
}
