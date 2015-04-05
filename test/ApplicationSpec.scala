import java.util.UUID

import helpers.{LoginHelper, WithLoggedInApplication}
import org.joda.time.DateTime
import org.specs2.mutable._
import org.specs2.runner._
import org.junit.runner._
import play.api.libs.mailer.MailerPlugin

import play.api.test._
import play.api.test.Helpers._
import play.filters.csrf.CSRF
import securesocial.controllers.Registration
import securesocial.core.RuntimeEnvironment

import scalikejdbc._
import securesocial.core.providers.utils.Mailer


/**
 * Add your spec here.
 * You can mock out a whole application including requests, plugins etc.
 * For more information, consult the wiki.
 */
class ApplicationSpec extends Specification {

  "Application" should {

    "send 404 on a bad request" in new WithApplication {
      route(FakeRequest(GET, "/boum")) must beNone
    }

    "render the index page" in new WithLoggedInApplication(un = "test-ss@urau.la", pw = "password") {

      val home = route(FakeRequest(GET, "/").withCookies(LoginHelper.cookie)).get

      status(home) must equalTo(OK)
      contentType(home) must beSome.which(_ == "text/html")
      contentAsString(home) must contain ("Your new application is ready.")
    }

    "サインアップページを表示する" in new WithApplication {
      // exec
      val signup = route(FakeRequest(GET, "/auth/signup")).get

      // then
      status(signup) must be_==(OK)
      contentType(signup) must beSome.which(_ == "text/html")
    }

    "リクエストされたメールアドレスに対し適切なメールを送信する" in new WithApplication {
      // UserService.findByEmailAndProvider == None -> 新規
      // UserService.findByEmailAndProvider == Option[BasicProfile] -> パスワードリセット

      // setup
      val csrfToken = CSRF.SignedTokenProvider.generateToken

      // when
      val signup = route(FakeRequest(POST, "/auth/signup")
        .withFormUrlEncodedBody("email" -> "test@urau.la", "csrfToken" -> csrfToken)
        .withSession("csrfToken" -> csrfToken)
      ).get

      // then
      status(signup) must be_==(SEE_OTHER)
      contentType(signup) must beNone
      cookies(signup).apply("PLAY_FLASH").value must be_==(
        "success=Thank+you.+Please+check+your+email+for+further+instructions&email=test%40urau.la"
      )
    }

    "トークンありサインアップページを表示する" in new WithApplication {
      // setup
      val uuid = UUID.fromString("07d0f108-1f23-457a-98dc-e50aa771a977")
      DB localTx { implicit session =>
        // ３日前にサインアップリクエストをした田中太郎さんを再現
        // 発行されたトークンはパスワードとして保存されている。ステータスはtemporary
        sql"""
              insert into accounts values(
              ${UUID.randomUUID}, ${"田中太郎"}, ${"test@utau.la"}, $uuid,
              ${"temporary"}, ${DateTime.now.minusDays(3)}
             )
        """.update.apply
      }

      // when
      val signup = route(FakeRequest(GET, s"/auth/signup/${uuid.toString}")).get

      // then
      status(signup) must be_==(OK)
      contentAsString(signup) must contain(s"/auth/signup/${uuid.toString}")
    }

    "無効なトークンでサインアップページを表示できない" in new WithApplication {
      // setup
      val uuid = UUID.fromString("576c4fa9-13a8-4d78-86fc-60173afecf8b")

      // when
      val signup = route(FakeRequest(GET, s"/auth/signup/${uuid.toString}")).get

      // then
      status(signup) must be_==(SEE_OTHER)
      cookies(signup).apply("PLAY_FLASH").value must be_==(
        "error=The+link+you+followed+is+invalid"
      )
    }

    "サインアップ処理をおこなう" in new WithApplication {
      // setup
      val uuid = UUID.fromString("e764f82f-91fe-4ee7-b806-e77614b5383a")
      DB localTx { implicit session =>
        // ３日前にサインアップリクエストをした田中太郎さんを再現
        // 発行されたトークンはパスワードとして保存されている。ステータスはtemporary
        sql"""
              insert into accounts values(
              ${UUID.randomUUID}, ${"田中太郎"}, ${"test-signup@utau.la"}, $uuid,
              ${"temporary"}, ${DateTime.now.minusDays(3)}
             )
        """.update.apply
      }
      val csrfToken = CSRF.SignedTokenProvider.generateToken

      val signup = route(FakeRequest(POST, s"/auth/signup/${uuid.toString}")
        .withFormUrlEncodedBody(
          "firstName" -> "田中",
          "lastName" -> "太郎",
          "password.password1" -> "password",
          "password.password2" -> "password",
          "csrfToken" -> csrfToken)
        .withSession("csrfToken" -> csrfToken)
      ).get

      status(signup) must be_==(SEE_OTHER)
      cookies(signup).apply("PLAY_FLASH").value must be_==(
        "success=Thank+you+for+signing+up.++You+can+log+in+now"
      )
    }
  }
}
