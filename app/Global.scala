import org.flywaydb.core.Flyway
import org.flywaydb.core.internal.util.jdbc.DriverDataSource
import play.api._
import scalikejdbc._
import scalikejdbc.config.DBs

/**
 * Created by tomohiro_urakawa on 2015/03/29.
 */
object Global extends GlobalSettings {

  override def onStart(app: Application) = {
    Logger.info("onStart="+app.configuration.getConfig("db"))
    Logger.info("db="+app.configuration.getString("db.default.driver"))
    app.configuration.getString("db.default.driver")
      .filter(_ == "org.postgresql.Driver").foreach { driver =>
      //TODO initialize function & trigger
      PostgresTriggers.migrate(app)
    }
  }
}

object PostgresTriggers {
  def migrate(app: Application) = {

    Logger.debug("postgres function/trigger migration files found. sp/migration/default")
    val(url, user, password) = parseUrl(
      app.configuration.getString("db.default.url").getOrElse(throw new IllegalArgumentException))

    val flyway = new Flyway
    flyway.setDataSource(new DriverDataSource(
      getClass.getClassLoader,
      app.configuration.getString("db.default.driver").getOrElse(throw new IllegalArgumentException()),
      url, user.getOrElse(""), password.getOrElse("")))
    flyway.setLocations("sp/migration/default")

    flyway.migrate()

  }

  // from play-flyway plugin
  val PostgresFullUrl = "^postgres://([a-zA-Z0-9_]+):([^@]+)@([^/]+)/([^\\s]+)$".r
  private def parseUrl(url: String) = url match {
      case PostgresFullUrl(username, password, host, dbname) =>
        ("jdbc:postgresql://%s/%s".format(host, dbname), Some(username), Some(password))
      case _ => throw new IllegalArgumentException
    }
}