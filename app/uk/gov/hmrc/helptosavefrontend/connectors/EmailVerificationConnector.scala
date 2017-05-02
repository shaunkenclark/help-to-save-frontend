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

import com.google.inject.{ImplementedBy, Singleton}
import uk.gov.hmrc.play.http.HeaderCarrier
import uk.gov.hmrc.play.config.ServicesConfig

import scala.concurrent.Future
import play.api.mvc.Results
import uk.gov.hmrc.helptosavefrontend.models.{EmailVerifyResult, emailSent}
import uk.gov.hmrc.helptosavefrontend.WSHttp

import scala.concurrent.ExecutionContext.Implicits.global

@ImplementedBy(classOf[EmailVerificationConnectorImpl])
trait EmailVerificationConnector {
  def sendVerificationEmail(email: String)(implicit hc: HeaderCarrier): Future[EmailVerifyResult]
  def isVerified(email: String)(implicit hc: HeaderCarrier): Future[Boolean]
}

@Singleton()
class EmailVerificationConnectorImpl extends EmailVerificationConnector with ServicesConfig {
  private val emailVerificationURL: String = baseUrl("email-verification")
  private val sendURL = "verification-requests"
  private def verifyURL(email: String) = s"verified-email-addresses/$email"
  private val http = WSHttp

  def sendVerificationEmail(email: String)(implicit hc: HeaderCarrier): Future[EmailVerifyResult] = Future.successful(emailSent)

  def isVerified(email: String)(implicit hc: HeaderCarrier): Future[Boolean] = http.GET(s"$emailVerificationURL/${verifyURL(email)}").map { _.status == 201}
}
