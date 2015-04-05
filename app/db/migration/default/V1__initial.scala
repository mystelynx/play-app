package db.migration.default

import java.sql.Connection
import scalikejdbc._

class V1__initial extends MigrationBase {

  override def migrate(connection: Connection) = DB(connection) withinTx { implicit session =>

    migrateAlways {

      sql"""
      create table accounts(
        id uuid primary key,
        name varchar(32) not null,
        email varchar(256) not null,
        password varchar(256) not null,
        status varchar(16),
        registered_at timestamp
      );
      """.update.apply

      sql"""
      create table account_updates(
        id bigserial primary key,
        account_id uuid not null,
        name varchar(32) not null,
        email varchar(256) not null,
        password varchar(256) not null,
        status varchar(16),
        registered_at timestamp,
        updated_by uuid,
        updated_at timestamp
      );
      """.update.apply
    }

    migrateOn('PostgreSQL) {

      sql"""
      create or replace function set_current_account() returns trigger AS $$$$
        BEGIN
          delete from accounts where id = NEW.account_id;

          if NEW.status != 'cancelled' then
          insert into accounts(id, name, email, password)
            values (NEW.account_id, NEW.name, NEW.email, NEW.password);
          end;

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



