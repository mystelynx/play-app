import org.joda.time.DateTime
import org.specs2.mutable._
import play.api.test.Helpers._
import play.api.test._
import scalikejdbc._

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

    "render the index page" in new WithApplication {

      val account_id = java.util.UUID.randomUUID()

      val actual_account = DB localTx { implicit session =>
        val id = sql"""insert into account_updates(account_id, name, password, updated_by, updated_at) values(
              ${account_id}, 'foo2', 'bar2',
              ${java.util.UUID.randomUUID}, ${DateTime.now})""".updateAndReturnGeneratedKey.apply

        sql"""select * from accounts where id = ${account_id}""".map(_.toMap).single.apply
      }

      actual_account.get("id") must be_==(account_id)
      actual_account.get("name") must be_==("foo2")
      actual_account.get("password") must be_==("bar2")

      val home = route(FakeRequest(GET, "/")).get

      status(home) must equalTo(OK)
      contentType(home) must beSome.which(_ == "text/html")
      contentAsString(home) must contain ("Your new application is ready.")
    }
  }
}
