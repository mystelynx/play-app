package helpers

import org.specs2.execute.{Result, AsResult}
import play.api.Logger
import play.api.mvc.Cookie
import play.api.test._
import play.api.test.Helpers._
import play.filters.csrf.CSRF

/**
 * Created by tomohiro_urakawa on 2015/04/04.
 */
object LoginHelper extends RouteInvokers with Writeables {

  val loginRequest = FakeRequest(POST, "/auth/authenticate/userpass")
    .withFormUrlEncodedBody("username" -> "tanaka", "password" -> "password")
    .withSession("csrfToken" -> CSRF.SignedTokenProvider.generateToken)

  var _cookie: Cookie = _

  def cookie = _cookie

  def login(): Unit = {

    val credentials = cookies(route(loginRequest).get)
    val id = credentials.get("id")

    _cookie = id.get
  }
}

trait WithLoggedInApplication extends WithApplication {
  override def around[T: AsResult](t: => T): Result = super.around {
    LoginHelper.login()
    t
  }
}
