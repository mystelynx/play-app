create or replace function set_current_account() returns trigger AS $$
BEGIN
  delete from accounts where id = NEW.account_id;
  insert into accounts(id, name, password)
  values (NEW.account_id, NEW.name, NEW.password);

  return NEW;
END;
$$ LANGUAGE 'plpgsql';

drop trigger if EXISTS current_account on account_updates;

create trigger current_account after insert on account_updates
for each ROW
execute procedure set_current_account();
