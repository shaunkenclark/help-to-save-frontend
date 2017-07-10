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

import cats.data.EitherT
import cats.instances.future._
import play.api.http.Status
import play.api.i18n.MessagesApi
import play.api.libs.json.{JsValue, Reads, Writes}
import play.api.mvc.{Result ⇒ PlayResult}
import play.api.test.FakeRequest
import play.api.test.Helpers._
import uk.gov.hmrc.auth.core._
import uk.gov.hmrc.helptosavefrontend.TestSupport
import uk.gov.hmrc.helptosavefrontend.connectors.NSIConnector.{SubmissionFailure, SubmissionSuccess}
import uk.gov.hmrc.helptosavefrontend.connectors._
import uk.gov.hmrc.helptosavefrontend.controllers.RegisterController.OAuthConfiguration
import uk.gov.hmrc.helptosavefrontend.models.HtsAuth.AuthWithConfidence
import uk.gov.hmrc.helptosavefrontend.models.MissingUserInfo.{Contact, Email}
import uk.gov.hmrc.helptosavefrontend.models._
import uk.gov.hmrc.helptosavefrontend.repositories.EnrolmentStore
import uk.gov.hmrc.helptosavefrontend.repositories.EnrolmentStore.{Enrolled, NotEnrolled}
import uk.gov.hmrc.helptosavefrontend.services.HelpToSaveService
import uk.gov.hmrc.helptosavefrontend.util.NINO
import uk.gov.hmrc.http.cache.client.CacheMap
import uk.gov.hmrc.play.http.HeaderCarrier

import scala.concurrent.{Await, ExecutionContext, Future}
import scala.concurrent.duration._

class RegisterControllerSpec extends TestSupport {

  val mockHtsService = mock[HelpToSaveService]
  val mockEnrolementStore = mock[EnrolmentStore]

  val nino = "WM123456C"

  val enrolment = Enrolment("HMRC-NI", Seq(EnrolmentIdentifier("NINO", nino)), "activated", ConfidenceLevel.L200)
  val enrolments = Enrolments(Set(enrolment))

  private val mockAuthConnector = mock[PlayAuthConnector]
  val mockSessionCacheConnector: SessionCacheConnector = mock[SessionCacheConnector]
  val testOAuthConfiguration = OAuthConfiguration(true, "url", "client-ID", "callback", List("scope1", "scope2"))

  val oauthAuthorisationCode = "authorisation-code"

  val register = new RegisterController(
    fakeApplication.injector.instanceOf[MessagesApi],
    mockHtsService,
    mockSessionCacheConnector,
    mockEnrolementStore)(
    fakeApplication, ec) {
    override val oauthConfig = testOAuthConfiguration
    override lazy val authConnector = mockAuthConnector
  }

  def mockEligibilityResult(nino: String, authorisationCode: String)(result: Either[MissingUserInfos, Option[UserInfo]]): Unit =
    (mockHtsService.checkEligibility(_: String, _: String)(_: HeaderCarrier))
      .expects(nino,authorisationCode, *)
      .returning(EitherT.pure(EligibilityCheckResult(result)))

  def failEligibilityResult(nino: String, authorisationCode: String): Unit =
    (mockHtsService.checkEligibility(_: String, _: String)(_: HeaderCarrier))
      .expects(nino,authorisationCode, *)
      .returning(EitherT.fromEither(Left("unexpected error during eligibility check")))

  def mockSessionCacheConnectorPut(result: Either[String, CacheMap]): Unit =
    (mockSessionCacheConnector.put(_: HTSSession)(_: Writes[HTSSession], _: HeaderCarrier))
      .expects(*, *, *)
      .returning(result.fold(
        e ⇒ Future.failed(new Exception(e)),
        Future.successful))

  def mockSessionCacheConnectorGet(mockHtsSession: Option[HTSSession]): Unit =
    (mockSessionCacheConnector.get(_: HeaderCarrier, _: Reads[HTSSession]))
      .expects(*, *)
      .returning(Future.successful(mockHtsSession))

  def mockCreateAccount(nSIUserInfo: NSIUserInfo)(response: Either[SubmissionFailure, SubmissionSuccess] = Right(SubmissionSuccess())): Unit =
    (mockHtsService.createAccount(_: NSIUserInfo)(_: HeaderCarrier, _: ExecutionContext))
      .expects(nSIUserInfo, *, *)
      .returning(EitherT.fromEither[Future](response))

  def mockPlayAuthWithRetrievals[A, B](predicate: Predicate)(result: Enrolments): Unit =
    (mockAuthConnector.authorise(_: Predicate, _: Retrieval[Enrolments])(_: HeaderCarrier))
      .expects(predicate, *, *)
      .returning(Future.successful(result))

  def mockPlayAuthWithWithConfidence(): Unit =
    (mockAuthConnector.authorise(_: Predicate, _: Retrieval[Unit])(_: HeaderCarrier))
      .expects(AuthWithConfidence, *, *)
      .returning(Future.successful(()))

  def mockEnrolmentStorePut(nino: NINO, imtpNeedsUpdate: Boolean)(result: Either[String,Unit]): Unit =
    (mockEnrolementStore.put(_: NINO, _: Boolean))
    .expects(nino, imtpNeedsUpdate)
    .returning(EitherT.fromEither[Future](result))

  def mockEnrolmentStoreGet(nino: NINO)(result: Either[String,EnrolmentStore.Status]): Unit =
    (mockEnrolementStore.get(_: NINO))
      .expects(nino)
      .returning(EitherT.fromEither[Future](result))

  "The RegisterController" when {

    "checking eligibility" must {
      def doConfirmDetailsRequest(): Future[PlayResult] = register.getAuthorisation(FakeRequest())

      def doConfirmDetailsCallbackRequest(authorisationCode: String): Future[PlayResult] =
        register.confirmDetails(Some(authorisationCode), None, None, None)(FakeRequest())


      "show the access denied page if a NINO is not available" in {
        mockPlayAuthWithRetrievals(AuthWithConfidence)(enrolments.copy(enrolments = Set.empty[Enrolment]))

        val result = doConfirmDetailsRequest()
        implicit val r = FakeRequest()
        redirectLocation(result) shouldBe Some(routes.RegisterController.accessDenied().absoluteURL)
      }

      "check if the user has already enrolled to HTS" in {
        inSequence{
          mockPlayAuthWithRetrievals(AuthWithConfidence)(enrolments)
          mockEnrolmentStoreGet(nino)(Right(Enrolled(false)))
        }

        Await.result(doConfirmDetailsRequest(), 5.seconds)
      }


      "return an InternalServerError if there is an error checking the enrolment status" in {
        inSequence{
          mockPlayAuthWithRetrievals(AuthWithConfidence)(enrolments)
          mockEnrolmentStoreGet(nino)(Left("Uh oh"))
        }

        val result = doConfirmDetailsRequest()
        status(result) shouldBe INTERNAL_SERVER_ERROR
      }

      "show the user the relevant page if they are already enrolled" in {
        inSequence{
          mockPlayAuthWithRetrievals(AuthWithConfidence)(enrolments)
          mockEnrolmentStoreGet(nino)(Right(Enrolled(false)))
        }

        val result = doConfirmDetailsRequest()
        contentAsString(result) should include("Successfully created account")
      }

      "redirect to OAuth to get an access token if enabled and the user is not already enrolled" in {
        inSequence{
          mockPlayAuthWithRetrievals(AuthWithConfidence)(enrolments)
          mockEnrolmentStoreGet(nino)(Right(NotEnrolled))
        }


        val result = doConfirmDetailsRequest()
        status(result) shouldBe Status.SEE_OTHER

        val (url, params) = redirectLocation(result).get.split('?').toList match {
          case u :: p :: Nil ⇒
            val paramList = p.split('&').toList
            val keyValueSet = paramList.map(_.split('=').toList match {
              case key:: value :: Nil ⇒ key → value
              case  _                 ⇒ fail(s"Could not parse query parameters: $p")
            }).toSet

            u → keyValueSet

          case _ ⇒ fail("Could not parse URL with query parameters")
        }

        url shouldBe testOAuthConfiguration.url
        params shouldBe (testOAuthConfiguration.scopes.map("scope" → _).toSet ++ Set(
          "client_id" -> testOAuthConfiguration.clientID,
          "response_type" -> "code",
          "redirect_uri" -> testOAuthConfiguration.callbackURL
        ))
      }


      "redirect to confirm-details with the NINO as the authorisation code if " +
        "redirects to OAUTH are disabled and the user is not already enrolled" in {
        val register = new RegisterController(
          fakeApplication.injector.instanceOf[MessagesApi],
          mockHtsService,
          mockSessionCacheConnector,
          mockEnrolementStore)(
          fakeApplication, ec) {
          override val oauthConfig = testOAuthConfiguration.copy(enabled = false)
          override lazy val authConnector = mockAuthConnector
        }

        inSequence{
          mockPlayAuthWithRetrievals(AuthWithConfidence)(enrolments)
          mockEnrolmentStoreGet(nino)(Right(NotEnrolled))
        }

        implicit val request = FakeRequest()
        val result = register.getAuthorisation(request)
        status(result) shouldBe Status.SEE_OTHER
        redirectLocation(result) shouldBe Some(routes.RegisterController.confirmDetails(Some(nino), None,None,None).absoluteURL())
      }


      "return a 500 if there is an error while getting the authorisation token" in {
        val result = register.confirmDetails(None, Some("uh oh"), None, None)(FakeRequest())
        status(result) shouldBe 500
      }

      "return user details if the user is eligible for help-to-save" in {
        val user = validUserInfo
        inSequence {
          mockPlayAuthWithRetrievals(AuthWithConfidence)(enrolments)
          mockEligibilityResult(nino, oauthAuthorisationCode)(Right(Some(user)))
          mockSessionCacheConnectorPut(Right(CacheMap("1", Map.empty[String, JsValue])))
        }

        val responseFuture: Future[PlayResult] = doConfirmDetailsCallbackRequest(oauthAuthorisationCode)
        val result = Await.result(responseFuture, 5.seconds)

        status(result) shouldBe Status.OK

        contentType(result) shouldBe Some("text/html")
        charset(result) shouldBe Some("utf-8")

        val html = contentAsString(result)

        html should include(user.forename)
        html should include(user.email)
        html should include(user.nino)
      }

      "display a 'Not Eligible' page if the user is not eligible" in {
        inSequence {
          mockPlayAuthWithRetrievals(AuthWithConfidence)(enrolments)
          mockEligibilityResult(nino, oauthAuthorisationCode)(Right(None))
        }

        val result = doConfirmDetailsCallbackRequest(oauthAuthorisationCode)

        status(result) shouldBe Status.SEE_OTHER

        redirectLocation(result) shouldBe Some("/help-to-save/register/not-eligible")
      }

      "report missing user info back to the user" in {
        inSequence {
          mockPlayAuthWithRetrievals(AuthWithConfidence)(enrolments)
          mockEligibilityResult(nino, oauthAuthorisationCode)(Left(MissingUserInfos(Set(Email, Contact))))
        }

        val responseFuture: Future[PlayResult] = doConfirmDetailsCallbackRequest(oauthAuthorisationCode)
        val result = Await.result(responseFuture, 5.seconds)

        status(result) shouldBe Status.OK

        contentType(result) shouldBe Some("text/html")
        charset(result) shouldBe Some("utf-8")

        val html = contentAsString(result)

        html should include("Email")
        html should include("Contact")
      }

      "return an error" when {

        def isError(result: Future[PlayResult]): Boolean =
          status(result) == 500

        // test if the given mock actions result in an error when `confirm_details` is called
        // on the controller
        def test(mockActions: ⇒ Unit): Unit = {
          mockActions
          val result = doConfirmDetailsCallbackRequest(oauthAuthorisationCode)
          isError(result) shouldBe true
        }

        "the nino is not available" in {
          test(
            mockPlayAuthWithRetrievals(AuthWithConfidence)(Enrolments(Set.empty[Enrolment]))
          )
        }

        "the eligibility check call returns with an error" in {
          test(
            inSequence {
              mockPlayAuthWithRetrievals(AuthWithConfidence)(enrolments)
              failEligibilityResult(nino, oauthAuthorisationCode)
            })
        }

        "if the user details fo not pass NS&I validation checks" in {
          val user = validUserInfo.copy(forename = " space-at-beginning")
          test(inSequence {
            mockPlayAuthWithRetrievals(AuthWithConfidence)(enrolments)
            mockEligibilityResult(nino, oauthAuthorisationCode)(Right(Some(user)))
          })
        }

        "there is an error writing to the session cache" in {
          test(inSequence {
            mockPlayAuthWithRetrievals(AuthWithConfidence)(enrolments)
            mockEligibilityResult(nino, oauthAuthorisationCode)(Right(Some(validUserInfo)))
            mockSessionCacheConnectorPut(Left("Bang"))
          })
        }
      }
    }


    "handling a getCreateAccountHelpToSave" must {

      "return 200" in {
        mockPlayAuthWithWithConfidence()
        val result = register.getCreateAccountHelpToSavePage(FakeRequest())
        status(result) shouldBe Status.OK
        contentType(result) shouldBe Some("text/html")
        charset(result) shouldBe Some("utf-8")
      }
    }

    "creating an account" must {
      def doCreateAccountRequest(): Future[PlayResult] = register.createAccountHelpToSave(FakeRequest())

      "retrieve the user info from session cache and post it using " +
        "the help to save service" in {
        inSequence {
          mockPlayAuthWithWithConfidence()
          mockSessionCacheConnectorGet(Some(HTSSession(Some(validNSIUserInfo))))
          mockCreateAccount(validNSIUserInfo)(Left(SubmissionFailure(None, "", "")))
        }
        Await.result(doCreateAccountRequest(), 5.seconds)
      }

      "add the user to the enrolment store if the account creation was successful" in {
        inSequence {
          mockPlayAuthWithWithConfidence()
          mockSessionCacheConnectorGet(Some(HTSSession(Some(validNSIUserInfo))))
          mockCreateAccount(validNSIUserInfo)()
          mockEnrolmentStorePut(validNSIUserInfo.nino, false)(Left(""))
        }
        Await.result(doCreateAccountRequest(), 5.seconds)
      }


      "indicate to the user that the creation was successful if the creation was successful" +
        "and the write to the enrolment store was succesful" in {
        inSequence {
          mockPlayAuthWithWithConfidence()
          mockSessionCacheConnectorGet(Some(HTSSession(Some(validNSIUserInfo))))
          mockCreateAccount(validNSIUserInfo)()
          mockEnrolmentStorePut(validNSIUserInfo.nino, false)(Right(()))
        }

        val result = doCreateAccountRequest()
        val html = contentAsString(result)
        html should include("Successfully created account")
      }

      "indicate to the user that the creation was not successful " when {

        def checkAccountCreationFailed(result: Future[PlayResult]): Unit = {
          val html = contentAsString(result)
          html should include("Account creation failed")
        }

        "the user details cannot be found in the session cache" in {
          inSequence {
            mockPlayAuthWithWithConfidence()
            mockSessionCacheConnectorGet(None)
          }

          checkAccountCreationFailed(doCreateAccountRequest())
        }

        "the help to save service returns with an error" in {
          inSequence {
            mockPlayAuthWithWithConfidence()
            mockSessionCacheConnectorGet(Some(HTSSession(Some(validNSIUserInfo))))
            mockCreateAccount(validNSIUserInfo)(Left(SubmissionFailure(None, "Uh oh", "Uh oh")))
          }

          checkAccountCreationFailed(doCreateAccountRequest())

        }

        "the write to the enrolment store fails" in {
          inSequence {
            mockPlayAuthWithWithConfidence()
            mockSessionCacheConnectorGet(Some(HTSSession(Some(validNSIUserInfo))))
            mockCreateAccount(validNSIUserInfo)()
            mockEnrolmentStorePut(validNSIUserInfo.nino, false)(Left(""))
          }

          checkAccountCreationFailed(doCreateAccountRequest())
        }

      }
    }

  }

}
