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

import hts.pages.{AuthorityWizardPage, Page}
import hts.utils.{Configuration, NINOGenerator}

class MissingUserInfoSteps extends Steps with NINOGenerator {

  When("""^they apply for Help to Save with missing surname$""") { () ⇒
    AuthorityWizardPage.authenticateUserNoSurname(s"${Configuration.host}/help-to-save/check-eligibility", 200, "Strong", nino.getOrElse(""))
  }

  When("""^they apply for Help to Save with missing address line$""") { () ⇒
    AuthorityWizardPage.authenticateUserMissingAddressLine(s"${Configuration.host}/help-to-save/check-eligibility", 200, "Strong", nino.getOrElse(""))
  }

  Then("""^they see that their surname couldn't be retrieved$""") { () ⇒
    Page.getPageContent should include("We couldn't retrieve the following details")
    Page.getPageContent should include ("Surname")
  }

  Then("""^they see that the first line of their address couldn't be retrieved$""") { () ⇒
    Page.getPageContent should include("We couldn't retrieve the following details")
    Page.getPageContent should include ("address1")
  }
}
