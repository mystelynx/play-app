import org.joda.time.DateTime
import org.specs2.mutable._
import org.specs2.runner._
import org.junit.runner._

import play.api.test._
import play.api.test.Helpers._


/**
 * Add your spec here.
 * You can mock out a whole application including requests, plugins etc.
 * For more information, consult the wiki.
 */
@RunWith(classOf[JUnitRunner])
class ApplicationSpec extends Specification {

  "Application" should {

    "send 404 on a bad request" in new WithApplication {
      route(FakeRequest(GET, "/boum")) must beNone
    }

    "render the index page" in new WithApplication {

      import scalikejdbc._

      DB localTx { implicit session =>
        val id = sql"""insert into account_updates(account_id, name, password, updated_by, updated_at) values(
              ${java.util.UUID.randomUUID}, 'foo', 'bar',
              ${java.util.UUID.randomUUID}, ${DateTime.now})""".updateAndReturnGeneratedKey.apply

        sql"""select * from account_updates where id = ${id}""".map(_.toMap).list.apply
      }

      val home = route(FakeRequest(GET, "/")).get

      status(home) must equalTo(OK)
      contentType(home) must beSome.which(_ == "text/html")
      contentAsString(home) must contain ("Your new application is ready.")
    }
  }
}
