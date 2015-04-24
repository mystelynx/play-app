import java.util.UUID
import javax.mail.Multipart

import com.icegreen.greenmail.util._
import controllers.MyUserService
import helpers._
import org.joda.time.DateTime
import org.specs2.execute.{Result, AsResult}
import org.specs2.mutable._
import play.api.Application
import play.api.http.ContentTypes
import play.api.libs.json.Json

import play.api.test._
import play.api.test.Helpers._
import play.filters.csrf.CSRF
import securesocial.controllers._
import securesocial.core.RuntimeEnvironment.Default
import securesocial.core.providers.UsernamePasswordProvider
import securesocial.core.services._
import securesocial.core._

import scalikejdbc._

import scala.concurrent.ExecutionContext.Implicits.global

import scala.collection.immutable.ListMap

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

    "render the index page" in new WithAuthenticatedApplication {

      val home = route(FakeRequest(GET, "/")).get

      status(home) must equalTo(OK)
      contentType(home) must beSome.which(_ == "text/html")
      contentAsString(home) must contain ("Your new application is ready!!!")
    }

    "render the index page(post)" in new WithAuthenticatedApplication {

      val home = route(FakeRequest(POST, "/").withTextBody(
        """{"age": 200}""").withHeaders(CONTENT_TYPE->ContentTypes.JSON)).get

      println(contentAsString(home))
      status(home) must equalTo(OK)
      contentType(home) must beSome.which(_ == "text/html")
      contentAsString(home) must contain ("Your new application is ready!!!")
    }

    "cannot render the index page" in new WithUnauthenticatedApplication {

      val home = route(FakeRequest(GET, "/")).get

      status(home) must equalTo(UNAUTHORIZED)
      contentType(home) must beSome.which(_ == "application/json")
      contentAsJson(home) must be_==(Json.obj("error" -> "Credentials required"))
    }

    "サインアップページを表示する" in new WithUnauthenticatedApplication {
      // exec
      val signup = route(FakeRequest(GET, "/signup")).get

      // then
      status(signup) must be_==(OK)
      contentType(signup) must beSome.which(_ == "text/html")
    }

    "リクエストされたメールアドレスに対し適切なメールを送信する" in new WithUnauthenticatedApplication {
      // UserService.findByEmailAndProvider == None -> 新規
      // UserService.findByEmailAndProvider == Option[BasicProfile] -> パスワードリセット

      // setup
      val csrfToken = CSRF.SignedTokenProvider.generateToken

      // when
      val signup = route(FakeRequest(POST, "/signup")
        .withFormUrlEncodedBody("email" -> "test@urau.la", "csrfToken" -> csrfToken)
        .withSession("csrfToken" -> csrfToken)
      ).get
      greenMail.waitForIncomingEmail(1) // メールが送出されるのを待つ

      // then
      status(signup) must be_==(SEE_OTHER)
      contentType(signup) must beNone
      cookies(signup).apply("PLAY_FLASH").value must be_==(
        "success=Thank+you.+Please+check+your+email+for+further+instructions&email=test%40urau.la"
      )

      val mail = greenMail.getReceivedMessages()(0)
      mail.getDataHandler.getContentType must startWith("multipart/mixed;")

      val part = mail.getContent.asInstanceOf[Multipart]
      part.getCount must be_==(1)

      val part0 = part.getBodyPart(0)
      part0.getContentType must be_==("text/html; charset=UTF-8")
      part0.getContent.asInstanceOf[String] must contain("/signup/")

      val tokenRegex = "//signup/([a-f0-9-]{36})".r
      val token = tokenRegex.findFirstMatchIn(part0.getContent.toString).map(_.group(1))
    }

    "トークンありサインアップページを表示する" in new WithUnauthenticatedApplication(
      storedTokens = Set("07d0f108-1f23-457a-98dc-e50aa771a977")) {

      // setup
      val token = storedTokens.head

      // when
      val signup = route(FakeRequest(GET, s"/signup/$token")).get

      // then
      status(signup) must be_==(OK)
      contentAsString(signup) must contain(s"/signup/$token")
    }

    "無効なトークンでサインアップページを表示できない" in new WithUnauthenticatedApplication {
      // setup
      val token = "576c4fa9-13a8-4d78-86fc-60173afecf8b"

      // when
      val signup = route(FakeRequest(GET, s"/signup/$token")).get

      // then
      status(signup) must be_==(SEE_OTHER)
      cookies(signup).apply("PLAY_FLASH").value must be_==(
        "error=The+link+you+followed+is+invalid"
      )
    }

    "サインアップ処理をおこなう" in new WithUnauthenticatedApplication(
      storedTokens = Set("e764f82f-91fe-4ee7-b806-e77614b5383a")) {
      // setup
      val token = storedTokens.head

      val csrfToken = CSRF.SignedTokenProvider.generateToken

      val signup = route(FakeRequest(POST, s"/signup/$token")
        .withFormUrlEncodedBody(
          "firstName" -> "田中",
          "lastName" -> "太郎",
          "password.password1" -> "password",
          "password.password2" -> "password",
          "csrfToken" -> csrfToken)
        .withSession("csrfToken" -> csrfToken)
      ).get
      greenMail.waitForIncomingEmail(1) // メールが送出されるのを待つ

      status(signup) must be_==(SEE_OTHER)
      cookies(signup).apply("PLAY_FLASH").value must be_==(
        "success=Thank+you+for+signing+up.++You+can+log+in+now"
      )

      val mail = greenMail.getReceivedMessages()(0)
      mail.getDataHandler.getContentType must startWith("multipart/mixed;")

      val part = mail.getContent.asInstanceOf[Multipart]
      part.getCount must be_==(1)

      val part0 = part.getBodyPart(0)
      part0.getContentType must be_==("text/html; charset=UTF-8")
      part0.getContent.asInstanceOf[String] must contain("/login")
    }

    "render the index page" in new WithApplication {

      val home = route(FakeRequest(GET, "/foo?bar=5&per_page=3&sort=name&name=あほ&agent=ばか")).get

      status(home) must equalTo(UNAUTHORIZED)
      contentType(home) must beSome.which(_ == "application/json")
      contentAsJson(home) must be_==(Json.obj("error"->"Credentials required"))
    }
  }
}
