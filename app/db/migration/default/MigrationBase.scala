package db.migration.default

import java.sql.Connection

import org.flywaydb.core.api.migration.jdbc.JdbcMigration
import play.api.Logger
import scalikejdbc._

/**
 * Created by tomohiro_urakawa on 2015/03/31.
 */
trait MigrationBase extends JdbcMigration {

  // do not show migration logs
  GlobalSettings.loggingSQLAndTime = LoggingSQLAndTimeSettings(enabled = false)

  def migrateAlways(block: => Unit)(implicit session: DBSession) = block

  def migrateOn(product: Symbol)(block: => Unit)(implicit session: DBSession) =
    if (product.name == session.connection.getMetaData.getDatabaseProductName)
      block
    else
      Logger.info(s"skipping $product migration. current database is ${session.connection.getMetaData.getDatabaseProductName}")
}
