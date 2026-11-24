-- Demo logins on existing seeded users, three per role: username <role>_<n>, password = username.
-- Customers and sellers are the busiest ones, so their row-scoped screens have real data.
-- Passwords are BCrypt ($2a$) hashes, which Spring's BCryptPasswordEncoder verifies.

create extension if not exists pgcrypto;

create temporary table login_pick (user_id bigint primary key, username varchar(50) not null) on commit drop;

-- customers with the most orders (and tickets as a tie-breaker)
insert into login_pick
select u.id, 'customer_' || row_number() over (order by o.cnt desc, t.cnt desc, u.id)
from app_user u
cross join lateral (select count(*) cnt from customer_order where customer_id = u.id) o
cross join lateral (select count(*) cnt from ticket where customer_id = u.id) t
where u.role = 'CUSTOMER'
order by o.cnt desc, t.cnt desc, u.id
limit 3;

-- one user of each of the three active sellers with the most orders
insert into login_pick
select u.id, 'seller_' || row_number() over (order by s.cnt desc, s.seller_id)
from (select o.seller_id, count(*) cnt
      from customer_order o join seller s on s.id = o.seller_id
      where s.status = 'ACTIVE'
      group by o.seller_id
      order by cnt desc, o.seller_id
      limit 3) s
cross join lateral (select id from app_user where seller_id = s.seller_id order by id limit 1) u;

-- staff: the first three users of each staff role
insert into login_pick
select id, lower(role) || '_' || n
from (select id, role, row_number() over (partition by role order by id) n
      from app_user
      where role in ('SUPPORT_T1', 'SUPPORT_T2', 'FINANCE', 'FRAUD', 'ADMIN')) s
where n <= 3;

update app_user u
set username      = p.username,
    password_hash = crypt(p.username, gen_salt('bf', 10)),
    status        = 'ACTIVE'
from login_pick p
where u.id = p.user_id;
