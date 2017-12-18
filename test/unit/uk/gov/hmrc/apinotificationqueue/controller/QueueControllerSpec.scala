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

import play.api.http.Status
import play.api.test.FakeRequest
import uk.gov.hmrc.play.test.{UnitSpec, WithFakeApplication}

class QueueControllerSpec extends UnitSpec with WithFakeApplication {


  "GET /messages" should {
    "return 200" in {
      val controller = new Queue()
      val result = controller.getAll()(FakeRequest("GET", "/messages"))
      status(result) shouldBe Status.OK
    }
  }

  "GET /message/:id" should {
    "return 200" in {
      val controller = new Queue()
      val uuid = java.util.UUID.randomUUID()
      val result = controller.get(uuid)(FakeRequest("GET", s"/message/$uuid"))
      status(result) shouldBe Status.OK
    }
  }
  "POST /queue" should {
    "return 201" in {
      val controller = new Queue()
      val result = controller.save()(FakeRequest("POST", "/queue"))
      status(result) shouldBe Status.CREATED
    }
  }

}
