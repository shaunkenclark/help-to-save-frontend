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

import javax.inject.Singleton

import com.google.inject.Inject
import play.api.i18n.{I18nSupport, MessagesApi}
import play.api.mvc._
import uk.gov.hmrc.helptosavefrontend.connectors.EmailVerificationConnector
import uk.gov.hmrc.helptosavefrontend.views
import uk.gov.hmrc.play.frontend.controller.FrontendController
import play.api.data.{Form, FormError}
import play.api.data.Forms._
import scala.concurrent.Future

@Singleton
class EmailVerificationController @Inject()(val messagesApi: MessagesApi,
                                            val verificationConnector: EmailVerificationConnector)   extends FrontendController with I18nSupport  {

  val emailForm: Form[String] = Form(
    single(
      "email-address" -> email
    )
  )

  def onPageLoad(): Action[AnyContent] = Action.async {request =>
    Future.successful(Ok(views.html.email_verification.send_confirmation(Some(emailForm))(request, messagesApi.preferred(request))))
  }

  def onPageSubmit(): Action[AnyContent] = Action.async {implicit request =>
    val boundForm = emailForm.bindFromRequest()

    boundForm.fold(formWithErrors => Future.successful(BadRequest(views.html.email_verification.send_confirmation(Some(formWithErrors)))),
      (emailAddress: String) => {
        verificationConnector.sendVerificationEmail(emailAddress, "jsdkhfkjds")
        Future.successful(Ok(views.html.email_verification.confirm_sent(emailAddress)))
      })
  }

}
