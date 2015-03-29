import org.joda.time.DateTime
import org.junit.runner._
import org.specs2.mutable._
import org.specs2.runner._
import play.api.test.Helpers._
import play.api.test._
import scalikejdbc.config.DBs

/**
 * add your integration spec here.
 * An integration test will fire up a whole play application in a real (or headless) browser
 */
@RunWith(classOf[JUnitRunner])
class IntegrationSpec extends Specification {

  "Application" should {

//    "work from within a browser" in new WithBrowser {
//
//      browser.goTo("http://localhost:" + port)
//
//      browser.pageSource must contain("Your new application is ready.")
//    }

    "render the index page" in new WithApplication {

      import scalikejdbc._

      DB localTx { implicit session =>
        val id = sql"""insert into account_updates(account_id, name, password, updated_by, updated_at) values(
              ${java.util.UUID.randomUUID}, 'foo', 'bar',
              ${java.util.UUID.randomUUID}, ${DateTime.now})""".updateAndReturnGeneratedKey.apply

        val events = sql"""select * from account_updates where id = ${id}""".map(_.toMap).list.apply
        println(events)

        events
      }

      val home = route(FakeRequest(GET, "/")).get

      status(home) must equalTo(OK)
      contentType(home) must beSome.which(_ == "text/html")
      contentAsString(home) must contain ("Your new application is ready.")
    }
  }
}
