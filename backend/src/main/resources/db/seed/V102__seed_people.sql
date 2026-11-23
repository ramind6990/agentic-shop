-- Sellers, seller users, customers (with addresses) and staff.
select setseed(0.102);

------------------------------------------------------------------------------------------------
-- reference lists
------------------------------------------------------------------------------------------------
create table seed.names (
    first_names text[] not null,
    last_names  text[] not null
);
insert into seed.names values (
'{James,Mary,Robert,Patricia,John,Jennifer,Michael,Linda,David,Elizabeth,William,Barbara,Richard,Susan,Joseph,Jessica,Thomas,Sarah,Christopher,Karen,Daniel,Lisa,Matthew,Nancy,Anthony,Betty,Mark,Sandra,Steven,Ashley,Andrew,Emily,Joshua,Michelle,Kevin,Amanda,Brian,Melissa,Ryan,Stephanie,Jacob,Rebecca,Nicholas,Laura,Eric,Emma,Jonathan,Olivia,Samuel,Sophia,Benjamin,Hannah,Lucas,Grace,Ethan,Chloe,Noah,Mia,Liam,Ava,Oliver,Isabella,Henry,Charlotte,Jack,Amelia,Leo,Harper,Felix,Lena,Lukas,Anna,Jonas,Marie,Maximilian,Sophie,Elias,Lea,Paul,Clara,Finn,Emilia,Oscar,Isla,George,Freya,Harry,Poppy,Arthur,Evie,Mateo,Valentina,Diego,Lucia,Raj,Priya,Arjun,Ananya,Wei,Mei,Hiroshi,Yuki,Omar,Layla,Ahmed,Fatima,Kofi,Amara,Aiden,Zoe,Caleb,Nora,Owen,Ella,Isaac,Ruby,Gabriel,Alice,Julian,Iris}',
'{Smith,Johnson,Williams,Brown,Jones,Garcia,Miller,Davis,Rodriguez,Martinez,Hernandez,Lopez,Gonzalez,Wilson,Anderson,Thomas,Taylor,Moore,Jackson,Martin,Lee,Perez,Thompson,White,Harris,Sanchez,Clark,Ramirez,Lewis,Robinson,Walker,Young,Allen,King,Wright,Scott,Torres,Nguyen,Hill,Flores,Green,Adams,Nelson,Baker,Hall,Rivera,Campbell,Mitchell,Carter,Roberts,Evans,Turner,Phillips,Parker,Collins,Edwards,Stewart,Morris,Murphy,Cook,Rogers,Morgan,Cooper,Peterson,Reed,Bailey,Bell,Kelly,Howard,Ward,Cox,Richardson,Wood,Watson,Brooks,Bennett,Gray,Hughes,Price,Sanders,Patel,Shah,Kim,Chen,Wang,Tanaka,Sato,Mueller,Schmidt,Schneider,Fischer,Weber,Meyer,Wagner,Becker,Hoffmann,Koch,Richter,Walsh,Byers,Byrne,Fraser,MacDonald,Campbell,Okafor,Mensah,Haddad,Khan,Silva,Santos,Costa,Rossi,Bianchi,Novak,Kowalski,Larsen,Andersen,Dubois,Moreau,Laurent}'
);

create table seed.city (
    id      serial primary key,
    country text not null,
    city    text not null,
    region  text,
    postal  text not null,   -- prefix used to build postal codes
    area    text not null,   -- phone area code
    weight  numeric not null
);
insert into seed.city (country, city, region, postal, area, weight) values
('US', 'New York', 'NY', '100', '212', 9), ('US', 'Brooklyn', 'NY', '112', '718', 5),
('US', 'Los Angeles', 'CA', '900', '213', 8), ('US', 'San Diego', 'CA', '921', '619', 3),
('US', 'San Francisco', 'CA', '941', '415', 3), ('US', 'Chicago', 'IL', '606', '312', 6),
('US', 'Houston', 'TX', '770', '713', 5), ('US', 'Dallas', 'TX', '752', '214', 4),
('US', 'Austin', 'TX', '787', '512', 3), ('US', 'San Antonio', 'TX', '782', '210', 3),
('US', 'Phoenix', 'AZ', '850', '602', 4), ('US', 'Philadelphia', 'PA', '191', '215', 4),
('US', 'Seattle', 'WA', '981', '206', 3), ('US', 'Denver', 'CO', '802', '303', 3),
('US', 'Boston', 'MA', '021', '617', 3), ('US', 'Portland', 'OR', '972', '503', 2),
('US', 'Atlanta', 'GA', '303', '404', 3), ('US', 'Miami', 'FL', '331', '305', 3),
('US', 'Orlando', 'FL', '328', '407', 2), ('US', 'Minneapolis', 'MN', '554', '612', 2),
('US', 'Nashville', 'TN', '372', '615', 2), ('US', 'Columbus', 'OH', '432', '614', 2),
('US', 'Charlotte', 'NC', '282', '704', 2), ('US', 'Raleigh', 'NC', '276', '919', 2),
('US', 'Salt Lake City', 'UT', '841', '801', 1), ('US', 'Kansas City', 'MO', '641', '816', 1),
('GB', 'London', 'Greater London', 'SW1A|EC1A|N1|SE1|W1D|E2', '20', 8),
('GB', 'Manchester', 'Greater Manchester', 'M1|M4|M14', '161', 3),
('GB', 'Birmingham', 'West Midlands', 'B1|B5|B15', '121', 3),
('GB', 'Leeds', 'West Yorkshire', 'LS1|LS6', '113', 2),
('GB', 'Glasgow', 'Scotland', 'G1|G12', '141', 2),
('GB', 'Bristol', 'Bristol', 'BS1|BS8', '117', 2),
('GB', 'Edinburgh', 'Scotland', 'EH1|EH3', '131', 2),
('GB', 'Liverpool', 'Merseyside', 'L1|L8', '151', 2),
('DE', 'Berlin', 'Berlin', '101', '30', 6), ('DE', 'Hamburg', 'Hamburg', '200', '40', 4),
('DE', 'München', 'Bayern', '803', '89', 4), ('DE', 'Köln', 'Nordrhein-Westfalen', '506', '221', 3),
('DE', 'Frankfurt am Main', 'Hessen', '603', '69', 3), ('DE', 'Stuttgart', 'Baden-Württemberg', '701', '711', 2),
('DE', 'Leipzig', 'Sachsen', '041', '341', 2),
('CA', 'Toronto', 'ON', 'M5V|M4C|M6G', '416', 6), ('CA', 'Vancouver', 'BC', 'V6B|V5K', '604', 4),
('CA', 'Montréal', 'QC', 'H2X|H3A', '514', 4), ('CA', 'Calgary', 'AB', 'T2P|T3A', '403', 2),
('CA', 'Ottawa', 'ON', 'K1P|K2B', '613', 2);

create table seed.street (
    country text   not null,
    names   text[] not null
);
insert into seed.street values
('US', '{Maple Avenue,Oak Street,Pine Street,Cedar Lane,Elm Street,Washington Avenue,Lake Drive,Park Avenue,Main Street,Sunset Boulevard,Hillcrest Road,Willow Way,River Road,Highland Avenue,Franklin Street,Jefferson Street,Lincoln Avenue,Meadow Lane,Chestnut Street,Spruce Court}'),
('GB', '{High Street,Station Road,Church Lane,Victoria Road,Green Lane,Manor Road,Park Road,Queens Road,Kings Road,Mill Lane,Albert Street,The Crescent,Grove Road,New Road,London Road}'),
('DE', '{Hauptstraße,Bahnhofstraße,Gartenstraße,Schulstraße,Lindenstraße,Bergstraße,Kirchstraße,Waldstraße,Ringstraße,Goethestraße,Schillerstraße,Mozartstraße}'),
('CA', '{King Street,Queen Street,Yonge Street,Bloor Street,Maple Road,Granville Street,Robson Street,Rue Sainte-Catherine,Wellington Street,Bank Street,Elgin Street,Spadina Avenue}');

-- picks a weighted random city for a country (r in [0,1))
create function seed.city_for(p_country text, r double precision) returns int
    language sql stable as
$$
select id from (
    select id, sum(weight) over (order by id) / sum(weight) over () as cum
    from seed.city where country = p_country
) c where c.cum > r order by c.cum limit 1
$$;

create function seed.postal_code(p_country text, prefix text, r1 double precision, r2 double precision)
    returns text language sql immutable as
$$
select case p_country
    when 'US' then prefix || lpad((floor(r1 * 100))::text, 2, '0')
    when 'DE' then prefix || lpad((floor(r1 * 100))::text, 2, '0')
    when 'GB' then seed.pick(string_to_array(prefix, '|'), r2) || ' ' || (1 + floor(r1 * 9))::text
                   || chr(65 + floor(r2 * 26)::int) || chr(65 + floor(r1 * 26)::int)
    when 'CA' then seed.pick(string_to_array(prefix, '|'), r2) || ' ' || (1 + floor(r1 * 9))::text
                   || chr(65 + floor(r2 * 26)::int) || (floor(r1 * 10))::text
end
$$;

create function seed.phone(p_country text, area text, r double precision) returns text
    language sql immutable as
$$
select case p_country
    when 'US' then '+1 (' || area || ') 555-' || lpad((floor(r * 10000))::text, 4, '0')
    when 'CA' then '+1 (' || area || ') 555-' || lpad((floor(r * 10000))::text, 4, '0')
    when 'GB' then '+44 7700 9' || lpad((floor(r * 100000))::text, 5, '0')
    when 'DE' then '+49 151 ' || lpad((floor(r * 100000000))::text, 8, '0')
end
$$;

------------------------------------------------------------------------------------------------
-- sellers: names are themed on the root category they mainly sell in
------------------------------------------------------------------------------------------------
create table seed.seller_theme (
    root  text   primary key,
    nouns text[] not null
);
insert into seed.seller_theme values
('electronics',      '{Electronics,Gadgets,Tech Supply,Digital}'),
('home-kitchen',     '{Home Goods,Kitchen Supply,Living,Home & Table}'),
('fashion',          '{Apparel,Outfitters,Boutique,Clothing}'),
('beauty',           '{Beauty,Apothecary,Skin Studio}'),
('sports-outdoors',  '{Outdoors,Sports,Adventure Supply}'),
('toys-games',       '{Toys,Toy Chest,Games}'),
('books',            '{Books,Booksellers,Book Barn}'),
('grocery',          '{Pantry,Provisions,Coffee & Tea}'),
('pet-supplies',     '{Pet Supply,Pet Shop,Paws}'),
('automotive',       '{Auto Parts,Auto Supply,Garage}'),
('garden-outdoor',   '{Garden Supply,Patio,Garden Center}'),
('health-household', '{Wellness,Health Store,Household}');

create table seed.seller_gen as
with root_weight as (
    select r.slug as root, sum(l.weight) as w
    from seed.cat r
    join seed.cat g on g.parent = r.slug
    join seed.leaf l on l.slug in (select slug from seed.cat where parent = g.slug)
    where r.parent is null
    group by r.slug
),
quota as (
    select root, greatest(8, round(${seed_sellers} * w / sum(w) over ()))::int as n from root_weight
),
combos as (
    select t.root, p.prefix || ' ' || n.noun || coalesce(' ' || nullif(s.suffix, ''), '') as name, random() as r
    from seed.seller_theme t
    cross join unnest(t.nouns) n(noun)
    cross join unnest('{Northwind,Maple Street,Blue Harbor,Golden Gate,Riverbend,Silver Pine,Oak & Ivy,Red Barn,Cedar Ridge,Brightside,Lakeshore,Summit Peak,Old Mill,Copper Kettle,Harborview,Willow Creek,Granite State,Sunnyvale,Evergreen,Bayside,Ironbridge,Prairie,Hilltop,Westbrook,Eastgate,Lighthouse,Stonegate,Meadowlark,Fox Hollow,Twin Oaks,Pinecrest,Sandstone,Crescent,Harvest Moon,Bluebird,Cobblestone,Starlight,Driftwood,Wildwood,Union Square}'::text[]) p(prefix)
    cross join unnest('{"",Outlet,Store,Depot,Direct,Market,Co.}'::text[]) s(suffix)
),
ranked as (
    select c.*, row_number() over (partition by c.root order by c.r) as rn from combos c
)
select row_number() over (order by r.root, r.rn) as n, r.root, r.name
from ranked r join quota q on q.root = r.root and r.rn <= q.n;

alter table seed.seller_gen
    add column country text, add column city_id int, add column joined_at timestamptz,
    add column status text, add column size_weight numeric, add column second_root text;

update seed.seller_gen set
    country = seed.wpick('{US,GB,DE,CA}', '{70,12,10,8}', random()),
    joined_at = timestamptz '2021-01-01' + random() * (timestamptz '2025-06-30' - timestamptz '2021-01-01'),
    status = seed.wpick('{ACTIVE,SUSPENDED,OFFBOARDED}', '{94,4,2}', random()),
    -- heavy-tailed seller size: a few big sellers, many small ones
    size_weight = round((1 / power(random() + 0.04, 1.3))::numeric, 3),
    second_root = case when random() < 0.25
        then (select slug from seed.cat where parent is null order by random() + n * 0 limit 1) end;

update seed.seller_gen set city_id = seed.city_for(country, random() + n * 0);
update seed.seller_gen set second_root = null where second_root = root;

insert into seller (name, slug, legal_name, description, email, country, rating_avg, status, joined_at)
select g.name,
       seed.slugify(g.name),
       g.name || case g.country when 'US' then ' LLC' when 'GB' then ' Ltd' when 'DE' then ' GmbH' else ' Inc.' end,
       'Independent ' || lower(c.name) || ' seller based in ' || ci.city || '. '
           || seed.pick('{Family-run since day one.,Fast shipping and friendly support.,Curated products we use ourselves.,Every order packed by hand.,Proudly small and independent.}'::text[], random() + g.n * 0),
       'support@' || seed.slugify(g.name) || '.example.com',
       g.country, null, g.status, g.joined_at
from seed.seller_gen g
join seed.cat c on c.slug = g.root
join seed.city ci on ci.id = g.city_id
order by g.n;

create table seed.seller_map as
select s.id as seller_id, g.root, g.second_root, g.size_weight, g.country, g.city_id
from seed.seller_gen g join seller s on s.slug = seed.slugify(g.name);

-- seller user accounts (1-2 per seller)
insert into app_user (email, full_name, phone, role, seller_id, status, created_at)
select lower(fn) || '.' || lower(ln) || '@' || s.slug || '.example.com',
       fn || ' ' || ln,
       seed.phone(m.country, ci.area, r3),
       'SELLER', s.id, 'ACTIVE', s.joined_at
from seller s
join seed.seller_map m on m.seller_id = s.id
join seed.city ci on ci.id = m.city_id
cross join lateral generate_series(1, case when random() + s.id * 0 < 0.4 then 2 else 1 end) k
cross join lateral (select random() + k * 0 as r1, random() + k * 0 as r2, random() + k * 0 as r3) r
cross join seed.names nm
cross join lateral (select seed.pick(nm.first_names, r.r1) as fn, seed.pick(nm.last_names, r.r2) as ln) x
on conflict (email) do nothing;

------------------------------------------------------------------------------------------------
-- staff
------------------------------------------------------------------------------------------------
insert into app_user (email, full_name, phone, role, seller_id, status, created_at)
select lower(fn) || '.' || lower(ln) || i || '@agenticshop.test',
       fn || ' ' || ln, null, role, null, 'ACTIVE',
       timestamptz '2021-06-01' + random() * interval '1000 days'
from (
    select i, role from (
        select generate_series(1, 30) as i, 'SUPPORT_T1' as role
        union all select generate_series(31, 40), 'SUPPORT_T2'
        union all select generate_series(41, 46), 'FINANCE'
        union all select generate_series(47, 52), 'FRAUD'
        union all select generate_series(53, 55), 'ADMIN'
    ) x
) roles
cross join seed.names nm
cross join lateral (select seed.pick(nm.first_names, random() + i * 0) as fn,
                           seed.pick(nm.last_names, random() + i * 0) as ln) x;

------------------------------------------------------------------------------------------------
-- customers + addresses
------------------------------------------------------------------------------------------------
create table seed.customer_gen as
select i,
       seed.wpick('{US,GB,CA,DE}', '{65,12,12,11}', random()) as country,
       random() as r_first, random() as r_last, random() as r_phone, random() as r_city,
       random() as r_created, random() as r_status, random() as r_domain, random() as r_second
from generate_series(1, ${seed_customers}) i;

alter table seed.customer_gen add column city_id int, add column first_name text, add column last_name text,
    add column email text;
update seed.customer_gen g set
    city_id = seed.city_for(g.country, g.r_city),
    first_name = seed.pick(nm.first_names, g.r_first),
    last_name = seed.pick(nm.last_names, g.r_last)
from seed.names nm;
update seed.customer_gen set
    email = lower(first_name) || '.' || lower(last_name) || i || '@'
            || seed.pick('{example.com,example.net,example.org}'::text[], r_domain);

insert into app_user (email, full_name, phone, role, seller_id, status, created_at)
select g.email,
       first_name || ' ' || last_name,
       case when r_phone < 0.85 then seed.phone(g.country, c.area, r_phone / 0.85) end,
       'CUSTOMER', null,
       case when r_status < 0.99 then 'ACTIVE' else 'SUSPENDED' end,
       timestamptz '2022-01-01' + r_created * (timestamptz '2026-10-31' - timestamptz '2022-01-01')
from seed.customer_gen g join seed.city c on c.id = g.city_id
order by i;

create table seed.customer_map as
select u.id as user_id, g.i, g.country, g.city_id, g.first_name, g.last_name, g.r_second
from seed.customer_gen g
join app_user u on u.email = g.email;

create index on seed.customer_map (user_id);

-- home address (default) for everyone
insert into address (user_id, label, recipient, line1, line2, city, region, postal_code, country, is_default)
select m.user_id, 'Home', m.first_name || ' ' || m.last_name,
       case when m.country = 'DE' then seed.pick(st.names, r1) || ' ' || (1 + floor(r2 * 120))::int
            else (10 + floor(r2 * 9900))::int || ' ' || seed.pick(st.names, r1) end,
       case when r3 < 0.25 then case m.country when 'DE' then 'Wohnung ' || (1 + floor(r3 * 40))::int
                                               else 'Apt ' || (1 + floor(r3 * 4 * 40))::int end end,
       c.city, c.region, seed.postal_code(m.country, c.postal, r1, r2), m.country, true
from seed.customer_map m
join seed.city c on c.id = m.city_id
join seed.street st on st.country = m.country
cross join lateral (select random() + m.i * 0 as r1, random() + m.i * 0 as r2, random() + m.i * 0 as r3) r;

-- a second address (work / family) for ~30% of customers, in another city of the same country
insert into address (user_id, label, recipient, line1, line2, city, region, postal_code, country, is_default)
select m.user_id, seed.pick('{Work,Office,Parents,Vacation Home}'::text[], r1),
       m.first_name || ' ' || m.last_name,
       case when m.country = 'DE' then seed.pick(st.names, r2) || ' ' || (1 + floor(r1 * 120))::int
            else (10 + floor(r1 * 9900))::int || ' ' || seed.pick(st.names, r2) end,
       null, c.city, c.region, seed.postal_code(m.country, c.postal, r2, r1), m.country, false
from seed.customer_map m
join seed.street st on st.country = m.country
cross join lateral (select random() + m.i * 0 as r1, random() + m.i * 0 as r2, random() + m.i * 0 as r3) r
join seed.city c on c.id = seed.city_for(m.country, r.r3)
where m.r_second < 0.3;
