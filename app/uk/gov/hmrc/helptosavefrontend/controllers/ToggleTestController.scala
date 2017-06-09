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

package uk.gov.hmrc.helptosavefrontend.controllers

import javax.inject.{Inject, Singleton}

import play.api.i18n.MessagesApi
import play.api.mvc.{Action, AnyContent}
import uk.gov.hmrc.helptosavefrontend.util.Toggles
import uk.gov.hmrc.play.frontend.controller.FrontendController

import scala.concurrent.Future

@Singleton
class ToggleTestController @Inject()() extends FrontendController {
  import uk.gov.hmrc.helptosavefrontend.util.TogglesFP._
  import com.github.fge.jackson.JsonLoader
  import com.github.fge.jsonschema.main._
  import com.fasterxml.jackson.databind.JsonNode
  import com.github.fge.jsonschema.core.report.ProcessingReport


  def tryToggles: Action[AnyContent] = Action.async { implicit request =>
    FEATURE[ProcessingReport, JsonNode]("json-nsi-schema-validation") enabled() thenDo {
      println("%%%%%%% SCHEMA VALIDATION")
      val jsonExample = """{"goodby": "world"}"""
      val jsonSchema =
        """{
          | "title": "Schema",
          | "type": "object",
          | "properties": {
          |   "hello": {
          |     "type": "string"
          |   }
          | },
          | "required": ["hello"]
          | }""".stripMargin

      val example = JsonLoader.fromString(jsonExample)
      val schema = JsonLoader.fromString(jsonSchema)
      val validator = JsonSchemaFactory.byDefault().getValidator
      val report = validator.validate(schema, example)

      if (report.isSuccess) {
        Right(example)
      } else {
        Left(report)
      }
    } otherwise {
      println("%%%%%%%%%%%%%%%%%%%% JSON validation not configured")
    }

    Future.successful(Ok)
  }
}

