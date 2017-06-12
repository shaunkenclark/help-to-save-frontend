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

package uk.gov.hmrc.helptosavefrontend.connectors

import java.util.Base64
import javax.inject.Singleton

import com.google.inject.ImplementedBy
import play.api.Logger
import play.api.http.Status
import play.api.libs.json._
import uk.gov.hmrc.helptosavefrontend.config.WSHttpProxy
import uk.gov.hmrc.helptosavefrontend.connectors.NSIConnector.{SubmissionFailure, SubmissionResult, SubmissionSuccess}
import uk.gov.hmrc.helptosavefrontend.models.NSIUserInfo
import uk.gov.hmrc.helptosavefrontend.util.JsErrorOps._
import uk.gov.hmrc.play.config.ServicesConfig
import uk.gov.hmrc.play.http._
import uk.gov.hmrc.helptosavefrontend.util._

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success, Try}

import com.fasterxml.jackson.databind.JsonNode
import com.github.fge.jsonschema.main.JsonSchemaFactory

@ImplementedBy(classOf[NSIConnectorImpl])
trait NSIConnector {
  def createAccount(userInfo: NSIUserInfo)(implicit hc: HeaderCarrier, ex: ExecutionContext): Future[SubmissionResult]
}

object NSIConnector {

  sealed trait SubmissionResult

  case class SubmissionSuccess() extends SubmissionResult

  case class SubmissionFailure(errorMessageId: Option[String], errorMessage: String, errorDetail: String) extends SubmissionResult

  implicit val submissionFailureFormat: Format[SubmissionFailure] = Json.format[SubmissionFailure]

}

@Singleton
class NSIConnectorImpl extends NSIConnector with ServicesConfig {

  import Toggles._
  import com.github.fge.jsonschema.core.report.ProcessingReport
  import com.github.fge.jackson.JsonLoader
  import com.github.fge.jsonschema.main._
  import com.fasterxml.jackson.databind.JsonNode

  val nsiUrl: String = baseUrl("nsi")
  val nsiUrlEnd: String = getString("microservice.services.nsi.url")
  val url = s"$nsiUrl/$nsiUrlEnd"

  val authorisationHeaderKey = getString("microservice.services.nsi.authorization.header-key")


  val authorisationDetails = {
    val user = getString("microservice.services.nsi.authorization.user")
    val password = getString("microservice.services.nsi.authorization.password")
    val encoding = getString("microservice.services.nsi.authorization.encoding")

    val encoded = Base64.getEncoder.encode(s"$user:$password".getBytes)
    s"Basic: ${new String(encoded, encoding)}"
  }

  val httpProxy = new WSHttpProxy

  private def checkOutGoingData(userInfo: NSIUserInfo): Either[ProcessingReport, JsonNode] = {
    val json = JsonLoader.fromString(Json.toJson(userInfo).toString)
    val schemaDef = """{
                      |    "type": "object",
                      |    "properties" : {
                      |	"nino": {
                      |	    "type": "string",
                      |	    "pattern": "^(([A-CEGHJ-PR-TW-Z][A-CEGHJ-NPR-TW-Z]) ?([0-9]{2}) ?([0-9]{2}) ?([0-9]{2}) ?([A-D]{1})|((XX) ?(99) ?(99) ?(99) ?(X)))$"
                      |	},
                      |	"forename": {
                      |	    "type": "string",
                      |	    "minLength" : 1,
                      |	    "maxLength" : 99
                      |	},
                      |	"surname": {
                      |	    "type": "string",
                      |	    "minLength" : 1,
                      |	    "maxLength" : 300
                      |	},
                      |	"dateOfBirth": {
                      |	    "type": "string",
                      |	    "pattern": "^[0-9]{4}[0-9]{2}[0-9]{2}$"
                      |	},
                      |	"contactDetails": {
                      |	    "type": "object",
                      |	    "properties": {
                      |		"email": {
                      |		    "type": "string",
                      |		    "maxLength": 252,
                      |		    "pattern" : "^.{1,64}@.{1,254}$"
                      |		},
                      |		"phoneNumber": {
                      |		    "type": "string"
                      |		},
                      |		"address": {
                      |		    "type": "array",
                      |		    "items": {
                      |			"type": "string",
                      |			"maxLength" : 35
                      |		    },
                      |		    "minimum": 1,
                      |		    "maximum": 5
                      |		},
                      |		"postCode" : {
                      |		    "type": "string",
                      |		    "minimum": 1,
                      |		    "maximum": 10
                      |		},
                      |		"countryCode" : {
                      |		    "type": "string",
                      |		    "minimum": 2,
                      |		    "maximum": 2
                      |		},
                      |		"communicationPreference": {
                      |		    "type": "string",
                      |		    "enum": ["00", "02"]
                      |		}
                      |	    },
                      |	    "required": ["communicationPreference"],
                      |	    "additionalProperties": false
                      |	},
                      |	"registrationChannel": {
                      |	    "type": "string",
                      |	    "enum": ["online", "callCentre"]
                      |	}
                      |    },
                      |    "required": ["nino","forename","surname","dateOfBirth","contactDetails", "registrationChannel"],
                      |    "additionalProperties": false
                      |}""".stripMargin
    val schema = JsonLoader.fromString(schemaDef)
    val validator = JsonSchemaFactory.byDefault().getValidator
    val report = validator.validate(schema, json)
    if (report.isSuccess) {
      Right(json)
    } else {
      Left(report)
    }
  }

  private def sendDataToNSI(userInfo: NSIUserInfo)(implicit hc: HeaderCarrier, ex: ExecutionContext): Future[SubmissionResult] =
  {
    Logger.info(s"Trying to create an account for ${userInfo.NINO}")
    httpProxy.post(url, userInfo, Map(authorisationHeaderKey → authorisationDetails))(
      NSIUserInfo.nsiUserInfoWrites, hc.copy(authorization = None))
      .map { response ⇒
        response.status match {
          case Status.CREATED ⇒
            Logger.info(s"Successfully created a NSI account for ${userInfo.NINO}")
            SubmissionSuccess()

          case Status.BAD_REQUEST ⇒
            Logger.error(s"Failed to create an account for ${userInfo.NINO} due to bad request")
            handleBadRequestResponse(response)

          case other ⇒
            Logger.warn(s"Unexpected error during creating account for ${userInfo.NINO}, status:$other ")
            SubmissionFailure(None, s"Something unexpected happened; response body: ${response.body}", other.toString)
        }
      }
  }


  private def handleBadRequestResponse(response: HttpResponse): SubmissionFailure = {
    Try(response.json) match {
      case Success(jsValue) ⇒
        Json.fromJson[SubmissionFailure](jsValue) match {
          case JsSuccess(submissionFailure, _) ⇒
            submissionFailure

          case e: JsError ⇒
            SubmissionFailure(None, s"Could not create NSI account errors; response body: ${response.body}", e.prettyPrint())
        }

      case Failure(error) ⇒
        SubmissionFailure(None, s"Could not read submission failure JSON response: ${response.body}", error.getMessage)

    }
  }

  override def createAccount(userInfo: NSIUserInfo)(implicit hc: HeaderCarrier, ex: ExecutionContext): Future[SubmissionResult] = {
    val result = FEATURE[ProcessingReport, JsonNode]("json-nsi-schema-validation") enabled() thenDo {
      checkOutGoingData(userInfo)
    }

    result match {
      case Right(_) => {
        println("$$$$$$$$$$$$$$$$$$$$$$$$ VALIDATION CONFIGURED - PASSES")
        sendDataToNSI(userInfo)
      }
      case Left(null) => {
        println("$$$$$$$$$$$$$$$$$$$$$$$$ VALIDATION NOT CONFIGURED")
        sendDataToNSI(userInfo)
      }
      case Left(_) => {
        println("$$$$$$$$$$$$$$$$$$$$$$$$ VALIDATION CONFIGURED - DOES NOT PASS")
        Future(SubmissionFailure(None, "Outgoing JSON failed to meet schema: ", result.left.toString))
      }
    }
  }
}
