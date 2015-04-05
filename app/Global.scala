import java.io.File
import java.lang.reflect.Constructor

import controllers.{MyUserService, User}
import org.flywaydb.core.Flyway
import org.flywaydb.core.internal.util.jdbc.DriverDataSource
import play.api.Mode.Mode
import play.api._
import scalikejdbc._
import scalikejdbc.config.DBs
import securesocial.core.providers.UsernamePasswordProvider
import securesocial.core.services.UserService
import securesocial.core.{IdentityProvider, RuntimeEnvironment}

import scala.collection.immutable.ListMap

/**
 * Created by tomohiro_urakawa on 2015/03/29.
 */
object Global extends GlobalSettings {

  /**
   * An implementation that checks if the controller expects a RuntimeEnvironment and
   * passes the instance to it if required.
   *
   * This can be replaced by any DI framework to inject it differently.
   *
   * @param controllerClass
   * @tparam A
   * @return
   */
  override def getControllerInstance[A](controllerClass: Class[A]): A = {
    val instance  = controllerClass.getConstructors.find { c =>
      val params = c.getParameterTypes
      params.length == 1 && params(0) == classOf[RuntimeEnvironment[User]]
    }.map {
      _.asInstanceOf[Constructor[A]].newInstance(ApplicationRuntimeEnvironment)
    }
    instance.getOrElse(super.getControllerInstance(controllerClass))
  }

  object ApplicationRuntimeEnvironment extends RuntimeEnvironment.Default[User] {
    protected override def include(p: IdentityProvider) = p.id ->   p

    override lazy val userService: UserService[User] = new MyUserService
    override lazy val providers = ListMap(
      include(new UsernamePasswordProvider[User](
        userService, avatarService, viewTemplates, passwordHashers))
    )

    //  override lazy val viewTemplates
    //  = new plugins.CustomTemplates(this) /// <====追加
  }
}


@deprecated
object PostgresTriggers {
  def migrate(app: Application) = {
    val(url, user, password) = parseUrl(
      app.configuration.getString("db.db.migration.default.url").getOrElse(throw new IllegalArgumentException))

    val flyway = new Flyway
    flyway.setDataSource(new DriverDataSource(
      getClass.getClassLoader,
      app.configuration.getString("db.db.migration.default.driver").getOrElse(throw new IllegalArgumentException()),
      url, user.getOrElse(""), password.getOrElse("")))

    // migrationのSQLが複数箇所にあるので自前でやる
    // 特定のDBの時だけ、、、というのが難しい
    flyway.setLocations("sp/migration/db.migration.default", "db/migration/default")

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