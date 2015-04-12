package db.migration.default

import java.sql.Connection
import scalikejdbc._

class V1__initial extends MigrationBase {

  override def migrate(connection: Connection) = DB(connection) withinTx { implicit session =>

    migrateAlways {

      sql"""
      drop table if exists users;
      create table users (
        id uuid primary key,
        name varchar(32) not null,
        email varchar(256) not null,
        password varchar(256) not null,
        role varchar(16) not null,
        status varchar(16) not null,
        registered_at timestamp not null
      );
      """.update.apply

      sql"""
      drop table if exists user_events;
      create table user_events (
        id bigserial primary key,
        account_id uuid not null,
        name varchar(32) not null,
        email varchar(256) not null,
        password varchar(256) not null,
        role varchar(16) not null,
        status varchar(16) not null,
        registered_at timestamp not null,
        updated_by uuid not null,
        updated_at timestamp not null
      );
      """.update.apply

      sql"""
      drop table if exists mail_tokens;
      create table mail_tokens (
        id uuid primary key,
        email varchar(256) not null,
        created_at timestamp not null,
        expires_at timestamp not null,
        is_signup boolean not null,
        status varchar(16) not null
      )
      """.update.apply

      sql"""
      drop table if exists mail_token_events;
      create table mail_token_events (
        id bigserial primary key,
        mail_token_id uuid not null,
        email varchar(256) not null,
        created_at timestamp not null,
        expires_at timestamp not null,
        is_signup boolean not null,
        status varchar(16) not null,
        updated_by uuid not null,
        updated_at timestamp not null
      )
      """
    }

    migrateOn('PostgreSQL) {

      // user_events -> users
      SQL(s"""
      create or replace function update_resource__users() returns trigger AS $$$$
        BEGIN
          ${
            entity.UserStatus.*.map {
              case entity.UserStatus(label, true) => {
                s"""
                  if NEW.status = '$label' then
                    delete from users where id = NEW.user_id;
                  end if;
                """
              }
              case entity.UserStatus(label, false) => {
                s"""
                  if NEW.status = '$label' then
                    with upsert as (
                      update users set
                        name = NEW.name,
                        email = NEW.email,
                        password = NEW.password,
                        role = NEW.role,
                        status = NEW.status,
                        registered_at = NEW.registered_at
                      where id = NEW.user_id returning id
                    )
                    insert into users
                      select
                        NEW.account_id,
                        NEW.name,
                        NEW.email,
                        NEW.password,
                        NEW.role,
                        NEW.status,
                        NEW.registered_at
                      where not exists (select id from upsert);
                    end if;
                """
              }
            }.mkString
          }
          return NEW;
        END; $$$$ LANGUAGE 'plpgsql';
      """).update.apply

      sql"""
      drop trigger if EXISTS on_user_events_registered on user_events;
      create trigger on_user_events_registered after insert on user_events
        for each ROW
        execute procedure update_resource__users();
      """.update.apply

      // mail_token_events -> mail_tokens
      SQL(s"""
      create or replace function update_resource__mail_tokens() returns trigger AS $$$$
        BEGIN
          ${
            entity.SimpleStatus.*.map {
              case entity.SimpleStatus(label, true) => {
                s"""
                  if NEW.status = '$label' then
                    delete from mail_tokens where id = NEW.mail_token_id;
                  end if;
                """
              }
              case entity.SimpleStatus(label, false) => {
                s"""
                  if NEW.status = '$label' then
                    with upsert as (
                      update mail_tokens set
                        email = NEW.email,
                        created_at = NEW.created_at,
                        expires_at = NEW.expires_at,
                        is_signup = NEW.is_signup,
                        status = NEW.status
                      where id = NEW.mail_token_id returning id
                    )
                    insert into mail_tokens
                      select
                        NEW.mail_token_id,
                        NEW.email,
                        NEW.created_at,
                        NEW.expires_at,
                        NEW.is_signup,
                        NEW.status
                      where not exists (select id from upsert);
                    end if;
                """
              }
            }.mkString
          }
          return NEW;
        END; $$$$ LANGUAGE 'plpgsql';
      """).update.apply

      sql"""
      drop trigger if EXISTS on_mail_token_events_registered on mail_token_events;
      create trigger on_mail_token_events_registered after insert on mail_token_events
        for each ROW
        execute procedure update_resource__mail_tokens();
      """.update.apply
    }
  }
}



