import java.lang.reflect.Constructor

import model.User
import org.joda.time.DateTime
import org.specs2.mutable._
import play.api.{Application, Logger, GlobalSettings}
import play.api.mvc.RequestHeader
import play.api.test.Helpers._
import play.api.test._
import securesocial.core.authenticator.{Authenticator, AuthenticatorBuilder}
import securesocial.core.providers.{MailToken, UsernamePasswordProvider}
import securesocial.core.services.{AuthenticatorService, SaveMode, UserService}
import securesocial.core.{BasicProfile, PasswordInfo, IdentityProvider, RuntimeEnvironment}

import scala.collection.immutable.ListMap
import scala.concurrent.Future

/**
 * add your integration spec here.
 * An integration test will fire up a whole play application in a real (or headless) browser
 */
class IntegrationSpec extends Specification {

  "Application" should {

//    "work from within a browser" in new WithBrowser {
//
//      browser.goTo("http://localhost:" + port)
//
//      browser.pageSource must contain("Your new application is ready.")
//    }

    val fakeApp = FakeApplication()

    "render the index page" in new WithApplication(app = fakeApp) {

      val home = route(FakeRequest(GET, "/sample")).get

      status(home) must equalTo(OK)
      contentType(home) must beSome.which(_ == "text/html")
      contentAsString(home) must contain ("Your new application is ready.")
    }
  }
}





