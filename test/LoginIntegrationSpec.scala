package com.flurdy.wishlist

import org.scalatest._
import org.scalatest.concurrent.{IntegrationPatience, ScalaFutures}
import org.scalatest.OptionValues._
import play.api.libs.ws.WSResponse
import scala.concurrent.{ExecutionContext, Future}

trait LoginIntegrationHelper extends RegistrationIntegrationHelper with CookieIntegrationHelper {

   val loginUrl  = s"$baseUrl/login"
   val logoutUrl = s"$baseUrl/logout"

   def login(username: String) = {
      val loginFormData = Map(
         "username" -> Seq(username),
         "password" -> Seq("simsalabim")
      )
      getWsClient().url(loginUrl).withFollowRedirects(false).post(loginFormData)
   }

   def registerAndLogin(username: String)(implicit ec: ExecutionContext) =
      for {
         _                <- register(username)
         loginResponse    <- login(username)
         session          =  findSessionCookie(loginResponse)
      } yield session

   def logout(session: Option[String]) =
      wsWithSession(logoutUrl, session).withFollowRedirects(false).get()
}

trait FrontPageIntegrationHelper extends IntegrationHelper  {


   val frontUrl = s"$baseUrl/"
   def frontpage() = getWsClient().url(frontUrl).get()
   def frontpageWithSession(session: Option[String]) = wsWithSession(frontUrl, session).get()
}

trait CookieIntegrationHelper {

  def findCookie(response: WSResponse, cookieName: String) =
      response.cookies.find(_.name.exists(_ == cookieName)).flatMap(_.value)

   def printCookies(response: WSResponse) =
      response.cookies.foreach( c => println( s"cookie is $c" ))

  def findSessionCookie(response: WSResponse) = findCookie(response, "PLAY_SESSION")

  def findFlashCookie(response: WSResponse)   = findCookie(response, "PLAY_FLASH")

}

class LoginIntegrationSpec extends AsyncFeatureSpec
      with GivenWhenThen with ScalaFutures with Matchers
      with IntegrationPatience with StartAndStopServer
      with LoginIntegrationHelper with FrontPageIntegrationHelper {

   info("As a wish recipient")
   info("I want to login to the Wish application")
   info("so that I can list my wishes")

   feature("Login flow") {

      scenario("Registration and log in") {

         val flow = for {
            _             <- register("Testerson")
            frontBefore   <- frontpage()
            loginResponse <- login("Testerson")
            session       =  findSessionCookie(loginResponse)
            frontAfter    <- frontpageWithSession(session)
         } yield(frontBefore, loginResponse, frontAfter)

         flow map { case(frontBefore, loginResponse, frontAfter) =>
            Given("Registered as a member of Wish")
            And("not logged in")
            val loginFormBefore = ScalaSoup.parse(frontBefore.body).select("#login-box input").headOption
            loginFormBefore shouldBe defined

            When("submitting the login form")
            loginResponse.status shouldBe 303
            loginResponse.header("Location").headOption.value shouldBe "/"
            findFlashCookie(loginResponse).value shouldBe "message=You+have+logged+in"

            Then("should be logged in")
            val loginFormAfter = ScalaSoup.parse(frontAfter.body).select("#login-box input").headOption
            loginFormAfter shouldBe None
            val logoutLink = ScalaSoup.parse(frontAfter.body).select("#logout-box li a").headOption
            logoutLink.value.text shouldBe "testerson"
         }
      }

      scenario("Can log out"){

         val flow = for {
            _              <- register("Testerson")
            loginResponse  <- login("Testerson")
            session        =  findSessionCookie(loginResponse)
            frontLogin     <- frontpageWithSession(session)
            logoutResponse <- logout(session)
            frontLogout    <- frontpage()
         }  yield(session, frontLogin, logoutResponse, frontLogout)

         flow map { case(session, frontLogin, logoutResponse, frontLogout) =>

            Given("a logged in user")
            val logoutLink = ScalaSoup.parse(frontLogin.body).select("#logout-box li a").headOption
            logoutLink.value.text shouldBe "testerson"
            session shouldBe defined
            session.value.length should be > 5

            When("logging out")
            logoutResponse.status shouldBe 303
            logoutResponse.header("Location").headOption.value shouldBe "/"
            findFlashCookie(logoutResponse).value shouldBe "message=You+have+been+logged+out"

            Then("should be logged out")
            val loggedOutLink = ScalaSoup.parse(frontLogout.body).select("#logout-box li a").headOption
            loggedOutLink.value.attr("href") shouldBe "/login.html"
            loggedOutLink.value.text shouldBe "log in"
         }
      }

      scenario("Logged in, out and in again"){
         val flow = for {
            _              <- register("Testerson")
            loginResponse1 <- login("Testerson")
            session1       =  findSessionCookie(loginResponse1)
            frontBefore    <- frontpageWithSession(session1)
            _              <- logout(session1)
            frontLogout    <- frontpage()
            loginResponse2 <- login("Testerson")
            session2       =  findSessionCookie(loginResponse2)
            frontAfter     <- frontpageWithSession(session2)
         } yield (frontBefore, frontLogout, loginResponse2, frontAfter)

         flow map { case(frontBefore, frontLogout, loginResponse, frontAfter) =>

            Given("a logged in user")
            val logoutLink = ScalaSoup.parse(frontBefore.body).select("#logout-box li a").headOption
            logoutLink.value.text shouldBe "testerson"

            When("logging out")
            val loggedOutLink = ScalaSoup.parse(frontLogout.body).select("#logout-box li a").headOption
            loggedOutLink.value.text shouldBe "log in"

            And("logging in again")
            loginResponse.status shouldBe 303
            loginResponse.header("Location").headOption.value shouldBe "/"

            Then("should be logged in")
            val logout2Link = ScalaSoup.parse(frontAfter.body).select("#logout-box li a").headOption
            logout2Link.value.text shouldBe "testerson"

         }
      }
   }
}