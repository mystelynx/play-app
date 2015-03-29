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
      PostgresTriggers.initialize
    }
  }
}

object PostgresTriggers {
  def initialize = {
    DBs.setupAll()
    DB localTx { implicit session =>
      sql"""
            create or replace function set_current_account() returns trigger AS $$$$
            BEGIN
              delete from accounts where id = NEW.account_id;
              insert into accounts(id, name, password)
              values (NEW.account_id, NEW.name, NEW.password);

              return NEW;
            END;
            $$$$ LANGUAGE 'plpgsql';
         """.update.apply;

      sql"""
            drop trigger if EXISTS current_account on account_updates;
         """.update.apply

      sql"""
            create trigger current_account after insert on account_updates
            for each ROW
            execute procedure set_current_account();
         """.update.apply()
    }
  }
}