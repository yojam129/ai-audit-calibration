create table if not exists sys_user (
  id bigint primary key auto_increment,
  org_id bigint not null,
  username varchar(64) not null,
  password_hash varchar(100) not null,
  display_name varchar(64) not null,
  enabled boolean not null default true,
  token_version bigint not null default 1,
  unique key uk_user_username(username)
);
create table if not exists sys_role (id bigint primary key auto_increment, role_code varchar(64) not null unique, role_name varchar(64) not null);
create table if not exists sys_permission (id bigint primary key auto_increment, permission_code varchar(128) not null unique, permission_name varchar(128) not null);
create table if not exists sys_user_role (user_id bigint not null, role_id bigint not null, primary key(user_id, role_id));
create table if not exists sys_role_permission (role_id bigint not null, permission_id bigint not null, primary key(role_id, permission_id));
