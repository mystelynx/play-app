import java.io.File

import org.flywaydb.core.Flyway
import org.flywaydb.core.internal.util.jdbc.DriverDataSource
import play.api.Mode.Mode
import play.api._
import scalikejdbc._
import scalikejdbc.config.DBs

/**
 * Created by tomohiro_urakawa on 2015/03/29.
 */
object Global extends GlobalSettings {

  override def onLoadConfig(config: Configuration, path: File, classLoader: ClassLoader, mode: Mode) = {
    Logger.debug("onloadconfig!!")
    Logger.debug(path.getAbsolutePath)
    Logger.debug(mode.toString)
    Logger.debug(configuration.toString)
    config
  }

  override def beforeStart(app: Application) = {
    Logger.debug("merge migration scripts db/sp")
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
    val(url, user, password) = parseUrl(
      app.configuration.getString("db.default.url").getOrElse(throw new IllegalArgumentException))

    val flyway = new Flyway
    flyway.setDataSource(new DriverDataSource(
      getClass.getClassLoader,
      app.configuration.getString("db.default.driver").getOrElse(throw new IllegalArgumentException()),
      url, user.getOrElse(""), password.getOrElse("")))

    // migrationのSQLが複数箇所にあるので自前でやる
    // 特定のDBの時だけ、、、というのが難しい
    flyway.setLocations("sp/migration/default", "db/migration/default")

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