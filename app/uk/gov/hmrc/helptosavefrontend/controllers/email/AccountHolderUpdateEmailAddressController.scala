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

package uk.gov.hmrc.helptosavefrontend.controllers.email

import cats.data.EitherT
import cats.instances.future._
import cats.instances.string._
import cats.syntax.eq._
import com.google.inject.Inject
import play.api.Application
import play.api.i18n.{I18nSupport, MessagesApi}
import play.api.mvc.{Action, AnyContent, Request, Result}
import uk.gov.hmrc.helptosavefrontend.config.{FrontendAppConfig, FrontendAuthConnector}
import uk.gov.hmrc.helptosavefrontend.connectors.{EmailVerificationConnector, NSIConnector}
import uk.gov.hmrc.helptosavefrontend.controllers.HelpToSaveAuth
import uk.gov.hmrc.helptosavefrontend.controllers.email.AccountHolderUpdateEmailAddressController.UpdateEmailError
import uk.gov.hmrc.helptosavefrontend.forms.UpdateEmailForm
import uk.gov.hmrc.helptosavefrontend.models.{EnrolmentStatus, HtsContext}
import uk.gov.hmrc.helptosavefrontend.services.HelpToSaveService
import uk.gov.hmrc.helptosavefrontend.util.{Crypto, Email, EmailVerificationParams, NINO, toFuture}
import uk.gov.hmrc.helptosavefrontend.views
import uk.gov.hmrc.play.http.HeaderCarrier

import scala.concurrent.{ExecutionContext, Future}

class AccountHolderUpdateEmailAddressController @Inject() (val helpToSaveService:          HelpToSaveService,
                                                           frontendAuthConnector:          FrontendAuthConnector,
                                                           val emailVerificationConnector: EmailVerificationConnector,
                                                           nSIConnector:                   NSIConnector
)(implicit app: Application, crypto: Crypto, val messagesApi: MessagesApi, ec: ExecutionContext)
  extends HelpToSaveAuth(app, frontendAuthConnector) with VerifyEmailBehaviour with I18nSupport {

  implicit val userType: UserType = UserType.AccountHolder

  def getUpdateYourEmailAddress(): Action[AnyContent] = authorisedForHtsWithInfo{ implicit request ⇒ implicit htsContext ⇒
    checkIfAlreadyEnrolled{
      case (_, email) ⇒
        Ok(views.html.email.update_email_address(email, UpdateEmailForm.verifyEmailForm))
    }
  }(redirectOnLoginURL = routes.AccountHolderUpdateEmailAddressController.getUpdateYourEmailAddress().url)

  def onSubmit(): Action[AnyContent] = authorisedForHtsWithInfo { implicit request ⇒ implicit htsContext ⇒
    checkIfAlreadyEnrolled{
      case (nino, _) ⇒
        sendEmailVerificationRequest(nino)
    }
  } (redirectOnLoginURL = routes.AccountHolderUpdateEmailAddressController.onSubmit().url)

  def emailVerified(emailVerificationParams: String): Action[AnyContent] = authorisedForHtsWithInfo{ implicit request ⇒ implicit htsContext ⇒
    handleEmailVerified(
      emailVerificationParams,
      params ⇒
        checkIfAlreadyEnrolled {
          case (nino, _) ⇒
            handleEmailVerified(nino, params)
        }
    )
  } (redirectOnLoginURL = routes.AccountHolderUpdateEmailAddressController.emailVerified(emailVerificationParams).url)

  def getEmailUpdated: Action[AnyContent] = authorisedForHts { implicit request ⇒ implicit htsContext: HtsContext ⇒
    Ok(views.html.email.we_updated_your_email())
  } (redirectOnLoginURL = routes.AccountHolderUpdateEmailAddressController.getEmailUpdated().url)

  def getEmailUpdateError: Action[AnyContent] = authorisedForHts { implicit request ⇒ implicit htsContext: HtsContext ⇒
    Ok(views.html.email.we_couldnt_update_your_email())
  } (redirectOnLoginURL = routes.AccountHolderUpdateEmailAddressController.getEmailUpdateError().url)

  private def handleEmailVerified(nino: NINO, emailVerificationParams: EmailVerificationParams)(
      implicit
      request:    Request[AnyContent],
      htsContext: HtsContext
  ): Future[Result] = {
    if (emailVerificationParams.nino =!= nino) {
      logger.warn("Email was verified but nino in URL did not match nino for user")
      InternalServerError
    } else {
      htsContext.userDetails match {

        case None ⇒
          logger.warn(s"For NINO [$nino]: email was verified but could not find user info ")
          InternalServerError

        case Some(Left(missingUserInfos)) ⇒
          logger.warn(s"For NINO [$nino]: email was verified but missing some user info " +
            s"(${missingUserInfos.missingInfo.mkString(",")}")
          InternalServerError

        case Some(Right(nsiUserInfo)) ⇒
          val result: EitherT[Future, UpdateEmailError, Unit] = for {
            _ ← nSIConnector.updateEmail(nsiUserInfo.updateEmail(emailVerificationParams.email)).leftMap(UpdateEmailError.NSIError)
            _ ← helpToSaveService.storeConfirmedEmail(emailVerificationParams.email, nino).leftMap[UpdateEmailError](UpdateEmailError.EmailMongoError)
          } yield ()

          result.fold({
            case UpdateEmailError.NSIError(e) ⇒
              logger.warn(s"For NINO [$nino]: Could not update email with NS&I: $e")
              SeeOther(routes.AccountHolderUpdateEmailAddressController.getEmailUpdateError().url)

            case UpdateEmailError.EmailMongoError(e) ⇒
              logger.warn(s"For NINO [$nino]: Email updated with NS&I but could not write email to email mongo store. Redirecting back to NS&I")
              SeeOther(uk.gov.hmrc.helptosavefrontend.controllers.routes.NSIController.goToNSI().url)
          }, { _ ⇒
            logger.info(s"For NINO [$nino]: successfully updated email with NS&I")
            SeeOther(routes.AccountHolderUpdateEmailAddressController.getEmailUpdated().url)
          })
      }
    }
  }

  /**
   * Use the enrolment store and email store to see if the user is enrolled
   */
  private def checkIfAlreadyEnrolled(ifEnrolled: (NINO, Email) ⇒ Future[Result])(implicit htsContext: HtsContext, hc: HeaderCarrier): Future[Result] = {
    val enrolled: EitherT[Future, String, (NINO, EnrolmentStatus, Option[Email])] = for {
      nino ← EitherT.fromOption[Future](htsContext.nino, "Could not find NINO")
      enrolmentStatus ← helpToSaveService.getUserEnrolmentStatus(nino)
      maybeEmail ← helpToSaveService.getConfirmedEmail(nino)
    } yield (nino, enrolmentStatus, maybeEmail)

    enrolled.fold[Future[Result]]({
      error ⇒
        logger.warn(s"Could not check enrolment status: $error")
        InternalServerError
    }, {
      case (nino, enrolmentStatus, maybeEmail) ⇒
        (enrolmentStatus, maybeEmail) match {
          case (EnrolmentStatus.NotEnrolled, _) ⇒
            // user is not enrolled in this case
            logger.warn(s"For NINO [$nino]: user was not enrolled")
            InternalServerError

          case (EnrolmentStatus.Enrolled(_), None) ⇒
            // this should never happen since we cannot have created an account
            // without a successful write to our email store
            logger.warn(s"For NINO [$nino]: user was enrolled but had no stored email")
            InternalServerError

          case (EnrolmentStatus.Enrolled(_), Some(email)) ⇒
            ifEnrolled(nino, email)

        }
    }).flatMap(identity)
  }
}

object AccountHolderUpdateEmailAddressController {

  private sealed trait UpdateEmailError

  private object UpdateEmailError {

    case class NSIError(message: String) extends UpdateEmailError

    case class EmailMongoError(message: String) extends UpdateEmailError

  }

}

