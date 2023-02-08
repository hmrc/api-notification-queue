/*
 * Copyright 2023 HM Revenue & Customs
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

package uk.gov.hmrc.apinotificationqueue.modules

import com.google.inject.AbstractModule
import uk.gov.hmrc.apinotificationqueue.repository.NotificationMongoRepository
import uk.gov.hmrc.customs.api.common.logging.CdsLogger

import javax.inject._
import scala.concurrent.{ExecutionContext, Future}

/**
 * Throwaway code - run once in each managed environment and then remove.
 * Also remove reference to StartModule in application.conf
 */
@Singleton
class AppStart @Inject()(notificationRepository: NotificationMongoRepository,
                         logger: CdsLogger)
                        (implicit ec: ExecutionContext) {

  logger.info("running AppStart module to drop indexes")
  notificationRepository.collection.indexesManager.list().flatMap { indexes =>
    indexes.find { index =>
      index.name.contains("clientId-xConversationId-Index")
    }.map { _ =>
      logger.info("dropping clientId-xConversationId-Index")
      notificationRepository.collection.indexesManager.drop("clientId-xConversationId-Index").map { res =>
        logger.info(s"number of indexes dropped for clientId-xConversationId-Index: $res")
      }
    }.getOrElse(Future.successful(()))
  }

  notificationRepository.collection.indexesManager.list().flatMap { indexes =>
    indexes.find { index =>
      index.name.contains("clientId-xConversationId-datePulled-Index")
    }.map { _ =>
      logger.info("dropping clientId-xConversationId-datePulled-Index")
      notificationRepository.collection.indexesManager.drop("clientId-xConversationId-datePulled-Index").map { res =>
        logger.info(s"number of indexes dropped for clientId-xConversationId-datePulled-Index: $res")
      }
    }.getOrElse(Future.successful(()))
  }

}

class StartModule extends AbstractModule {
  override def configure(): Unit = {
    bind(classOf[AppStart]).asEagerSingleton()
  }
}
