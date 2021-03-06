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

package hts.pages

import org.openqa.selenium.WebDriver
import org.scalatest.{Assertions, Matchers}
import org.scalatest.concurrent.{Eventually, PatienceConfiguration}
import org.scalatest.selenium.WebBrowser
import org.scalatest.time.{Millis, Seconds, Span}

trait WebPage extends Matchers
  with WebBrowser
  with Eventually
  with PatienceConfiguration
  with Assertions {

  override implicit val patienceConfig: PatienceConfig = PatienceConfig(timeout  = scaled(Span(5, Seconds)), interval = scaled(Span(500, Millis)))

  def isCurrentPage(implicit driver: WebDriver): Boolean = false

  def back()(implicit driver: WebDriver): Unit = clickOn("ButtonBack")

  def nextPage()(implicit driver: WebDriver): Unit = find(CssSelectorQuery(".page-nav__link.page-nav__link--next")).foreach(click.on)

  def checkHeader(heading: String, text: String)(implicit driver: WebDriver): Boolean =
    find(cssSelector(heading)).exists(_.text === text)

}
