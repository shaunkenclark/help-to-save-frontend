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

import com.google.inject.Inject
import play.api.Play.current
import play.api.i18n.Messages.Implicits._
import play.api.mvc._
import uk.gov.hmrc.auth.core.AuthProvider.GovernmentGateway
import uk.gov.hmrc.helptosavefrontend.connectors.EligibilityConnector
import uk.gov.hmrc.play.frontend.controller.FrontendController
import uk.gov.hmrc.helptosavefrontend.{FrontendAuthConnector, views}
import uk.gov.hmrc.auth.core.{AuthProviders, AuthorisedFunctions, ConfidenceLevel, Enrolment}
import uk.gov.hmrc.auth.core.Retrievals.userDetailsUri

import scala.concurrent.Future

class HelpToSave @Inject()(eligibilityConnector: EligibilityConnector) extends FrontendController with AuthorisedFunctions {

  val nino = "A434387534D"
  val authConnector = FrontendAuthConnector

  val notEligible = Action.async { implicit request ⇒
    Future.successful(Ok(views.html.core.not_eligibile()))
  }

  val start =
    Action.async { implicit request ⇒
      authorised() {
        Future.successful(Ok(views.html.core.start()))
      }
    }

  //  val declaration =
  //    Action.async {
  //      implicit request ⇒
  //        authorised(Enrolment("IR-SA") and AuthProviders(GovernmentGateway)).retrieve(userDetailsUri) { uri =>
  //          println("%%%%%%%%%%%%%%%%%%%%%%% User details URI = " + uri)
  //          eligibilityConnector.checkEligibility(nino)
  //            .map(result ⇒
  //              Ok(result.fold(
  //                views.html.core.not_eligibile(),
  //                user ⇒ uk.gov.hmrc.helptosavefrontend.views.html.register.declaration(user)
  //              )))
  //        }
  //    }

  val declaration =
    Action.async {
      implicit request ⇒
        authorised(Enrolment("HMRC-NI").withConfidenceLevel(ConfidenceLevel.L100) and AuthProviders(GovernmentGateway)).retrieve(userDetailsUri) { (thing: Option[String]) =>
          val msg = thing.fold("UNKNOWN") { (theThing: String) =>
            "%%%%%%%%%%%%%%%%%%%%%%% User details THING = " + theThing
          }
          println(msg)
            eligibilityConnector.checkEligibility(nino)
              .map(result ⇒
                Ok(result.fold(
                  views.html.core.not_eligibile(),
                  user ⇒ uk.gov.hmrc.helptosavefrontend.views.html.register.declaration(user)
                )))
        }
    }
}
