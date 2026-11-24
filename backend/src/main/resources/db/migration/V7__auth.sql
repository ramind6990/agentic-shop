-- Login credentials. Only users that can sign in have them; the rest stay null.

alter table app_user add column username      varchar(50) unique;
alter table app_user add column password_hash varchar(100);
