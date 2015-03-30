package db.migration.default

import java.sql.Connection
import scalikejdbc._

import org.flywaydb.core.api.migration.jdbc.JdbcMigration

class V1__initial extends JdbcMigration {

  // migrationのSQLは非表示で良い
  GlobalSettings.loggingSQLAndTime = LoggingSQLAndTimeSettings(enabled = false)

  override def migrate(connection: Connection): Unit = DB(connection) withinTx { implicit session =>

      sql"""
      create table accounts(
        id uuid primary key,
        name varchar(32) not null,
        password varchar(256) not null
      );
      """.update.apply

      sql"""
      create table account_updates(
        id bigserial primary key,
        account_id uuid not null,
        name varchar(32) not null,
        password varchar(256) not null,
        updated_by uuid not null,
        updated_at timestamp not null default(current_timestamp),
        deleted_at timestamp
      );
      """.update.apply

      if (connection.getMetaData.getDatabaseProductName == "PostgreSQL") {
        sql"""
        create or replace function set_current_account() returns trigger AS $$$$
        BEGIN
          delete from accounts where id = NEW.account_id;
          insert into accounts(id, name, password)
          values (NEW.account_id, NEW.name, NEW.password);

          return NEW;
        END;
        $$$$ LANGUAGE 'plpgsql';
        """.update.apply

        sql"""
        drop trigger if EXISTS current_account on account_updates;
        """.update.apply

        sql"""
        create trigger current_account after insert on account_updates
          for each ROW
          execute procedure set_current_account();
        """.update.apply
    }
  }
}



