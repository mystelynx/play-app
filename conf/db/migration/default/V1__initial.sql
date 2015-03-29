create table accounts(
  id uuid primary key,
  name varchar(32) not null,
  password varchar(256) not null
);

create table account_updates(
  id bigserial primary key,
  account_id uuid not null,
  name varchar(32) not null,
  password varchar(256) not null,
  updated_by uuid not null,
  updated_at timestamp not null default(current_timestamp),
  deleted_by timestamp
);
