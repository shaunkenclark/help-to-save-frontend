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

package hts.steps

import cucumber.api.DataTable
import hts.pages.{AuthorityWizardPage, EligiblePage, Page}
import hts.utils.{Configuration, NINOGenerator}

import scala.collection.JavaConverters._
import scala.collection.mutable

class UserDetailsSteps extends Steps with NINOGenerator {

  var name: Option[String] = None
  var dateOfBirth: Option[String] = None
  var email: Option[String] = None

  Then("""^they see their details$"""){ () ⇒
    Page.getPageContent should include("Name: " + name.getOrElse(sys.error("Could not find name")))
    Page.getPageContent should include("National Insurance number: " + currentNINO)
    Page.getPageContent should include("Date of Birth: " + dateOfBirth.getOrElse(sys.error("Could not find DoB")))
    Page.getPageContent should include("Email: " + email.getOrElse(sys.error("Could not find email")))
  }
}
