import java.util.UUID
import javax.mail.Multipart

import com.icegreen.greenmail.util._
import controllers.MyUserService
import helpers.FakeGlobal
import org.joda.time.DateTime
import org.specs2.execute.{Result, AsResult}
import org.specs2.mutable._

import play.api.test._
import play.api.test.Helpers._
import play.filters.csrf.CSRF
import securesocial.controllers._
import securesocial.core.RuntimeEnvironment.Default
import securesocial.core.services._
import securesocial.core._

import scalikejdbc._

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

    "render the index page" in new WithApplication(
      app = FakeApplication(withGlobal = Option(FakeGlobal(model.User("fake-man"))))) {

      val home = route(FakeRequest(GET, "/sample")).get

      status(home) must equalTo(OK)
      contentType(home) must beSome.which(_ == "text/html")
      contentAsString(home) must contain ("Your new application is ready!!!")
    }

    "サインアップページを表示する" in new WithApplication {
      // exec
      val signup = route(FakeRequest(GET, "/auth/signup")).get

      // then
      status(signup) must be_==(OK)
      contentType(signup) must beSome.which(_ == "text/html")
    }

    abstract class WithApplicationAndMailer(val greenMail: GreenMail) extends WithApplication {
      override def around[T: AsResult](t : => T): Result = super.around {
        greenMail.start()
        val result = t

        result
      }
    }

    "リクエストされたメールアドレスに対し適切なメールを送信する" in new WithApplicationAndMailer(new GreenMail(ServerSetupTest.SMTP)) {
      // UserService.findByEmailAndProvider == None -> 新規
      // UserService.findByEmailAndProvider == Option[BasicProfile] -> パスワードリセット

      // setup
      val csrfToken = CSRF.SignedTokenProvider.generateToken
      val controller = new BaseRegistration[model.User]() {
        override implicit val env: RuntimeEnvironment[model.User] = new Default[model.User] {
          override val userService: UserService[model.User] = new MyUserService
        }
      }

      // when
      val signup = controller.handleStartSignUp(FakeRequest(POST, "/auth/signup")
        .withFormUrlEncodedBody("email" -> "test@urau.la", "csrfToken" -> csrfToken)
        .withSession("csrfToken" -> csrfToken)
      )
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
      part0.getContent.asInstanceOf[String] must contain("/auth/signup/")

      val tokenRegex = "//auth/signup/([a-f0-9-]{36})".r
      val token = tokenRegex.findFirstMatchIn(part0.getContent.toString).map(_.group(1))
      println(s"token=$token")
    }.pendingUntilFixed

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
    }.pendingUntilFixed

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
    }.pendingUntilFixed

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
    }.pendingUntilFixed
  }
}
