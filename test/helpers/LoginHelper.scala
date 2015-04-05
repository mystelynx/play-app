package helpers

import java.util.UUID

import org.joda.time.DateTime
import org.mindrot.jbcrypt.BCrypt
import org.specs2.execute.{Result, AsResult}
import play.api.Logger
import play.api.mvc.Cookie
import play.api.test._
import play.api.test.Helpers._
import play.filters.csrf.CSRF
import scalikejdbc._

/**
 * Created by tomohiro_urakawa on 2015/04/04.
 */
object LoginHelper extends RouteInvokers with Writeables {

  var _cookie: Cookie = _

  def cookie = _cookie

  def login(un: String, pw: String): Unit = {

    val credentials = cookies(
      route(
        FakeRequest(POST, "/auth/authenticate/userpass")
          .withFormUrlEncodedBody("username" -> un, "password" -> pw)
          .withSession("csrfToken" -> CSRF.SignedTokenProvider.generateToken)).get)
    val id = credentials.get("id")

    _cookie = id.get
  }
}

abstract class WithLoggedInApplication(un: String, pw: String) extends WithApplication {
  override def around[T: AsResult](t: => T): Result = super.around {
    DB localTx { implicit session =>
      sql"""
              insert into accounts values(
              ${UUID.randomUUID}, ${"田中太郎"}, ${un},
              ${BCrypt.hashpw(pw, BCrypt.gensalt)},
              ${"running"}, ${DateTime.now.minusDays(3)}
              )
        """.update.apply
    }
    LoginHelper.login(un, pw)
    t
  }
}
