package uk.gov.hmrc.helptosavefrontend.controllers

import play.api.mvc.{Action, AnyContent}
import play.api.mvc.Results._

import scala.concurrent.Future

trait EligibilityController {
  def onPageLoad(): Action[AnyContent]
}

class EligibilityControllerImpl {
  def onPageLoad() = Action.async {implicit request =>
    Future.successful(Ok("Hello World"))
  }
}
