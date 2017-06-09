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

package uk.gov.hmrc.helptosavefrontend.util

import uk.gov.hmrc.helptosavefrontend.config.ControllerConfiguration
import play.api.Logger
import java.time.Instant

object TogglesFP {
  case class FEATURE[L, R](name: String, default: L = null) {
    val logger = Logger(name)

    def enabled(): FEATURE_THEN[L, R] = {
      val cc = ControllerConfiguration
      try {
        val enabled = cc.controllerConfigs.getBoolean("toggles." + name + ".enabled")
        logger.info("FEATURE: " + name + " enabled.")
        FEATURE_THEN[L, R](this.name, logger, default, enabled)
      } catch {
        case ex: Exception => FEATURE_THEN(this.name, logger, default, false)
      }
    }

    def enabledWith(key: String): FEATURE_THEN_KEY[L, R] = {
      val cc = ControllerConfiguration
      var enabled = false
      try {
        enabled = cc.controllerConfigs.getBoolean("toggles." + name + ".enabled")
        val v = cc.controllerConfigs.getString("toggles." + name + "." + key)
        logger.info("FEATURE: " + name + " enabled with key " + key + " set to " + v)
        FEATURE_THEN_KEY[L, R](this.name, this.logger, default, enabled, v, true)
      } catch {
        case ex: Exception => new FEATURE_THEN_KEY[L, R](this.name, this.logger, default, enabled, "", false)
      }
    }
  }

  object FEATURE

  case class FEATURE_THEN[L, R](name: String, logger: Logger, default: L, isEnabled: Boolean) {
    def thenDo(action: => Either[L, R]): Either[L, R] = {
      if (isEnabled) {
        val startTime = Instant.now.toEpochMilli
        val result = action
        val endTime = Instant.now.toEpochMilli
        Logger.info("FEATURE: " + name + " executed in " + (endTime - startTime).toString + " milliseconds.")
        result
      } else {
        Left(default)
      }
    }
  }

  object FEATURE_THEN

  case class FEATURE_THEN_KEY[L, R](name: String, logger: Logger, default: L, isEnabled: Boolean, key: String, hasKey: Boolean) {
    def thenDo(action: String => Either[L, R]) = {
      if (isEnabled && hasKey) {
        val startTime = Instant.now.toEpochMilli
        val result = action(key)
        val endTime = Instant.now.toEpochMilli
        Logger.info("FEATURE: " + name + " executed in " + (endTime - startTime).toString + " milliseconds.")
        Right(result)
      } else {
        Left(default)
      }
    }
  }

  object FEATURE_THEN_KEY

  implicit class EitherExtension[L, R](e: Either[L, R]) {
    def otherwise(action: => Unit) = {
      val eLeftGOE: Any = e.left.getOrElse(null)
      if (e.isLeft && e.left.getOrElse(null) == null) {
          action
      }
    }
  }
}
