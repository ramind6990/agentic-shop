-- Products: brand, seller, attribute values, title, price, description, images, inventory.
-- Title, attributes, price and description are all derived from the same picked attribute values.
select setseed(0.103);

-- planner statistics for the tables filled by previous seed steps
analyze;

-- category-level selling copy appended to descriptions
create table seed.root_text (
    root       text   primary key,
    paragraphs text[] not null
);
insert into seed.root_text values
('electronics', '{"Every unit ships factory-sealed with the manufacturer''s warranty and all original accessories.","Compatible with standard USB-C chargers and accessories. Firmware updates are available from the manufacturer''s support site.","Questions about compatibility? Message the seller before you buy and we will help you choose the right model."}'),
('home-kitchen', '{"Designed for everyday use and backed by a satisfaction guarantee.","Check the care instructions on the label: most items are easy to clean and built to last for years.","Gift-ready packaging. Free returns within the return window if it is not the right fit for your home."}'),
('fashion', '{"Size check: this item runs true to size. If you are between sizes, we recommend sizing up.","Machine wash cold with similar colors, tumble dry low. Do not bleach.","Free exchanges on size and color within the return window."}'),
('beauty', '{"Dermatologist tested. Not tested on animals.","Patch test before first use. Store in a cool, dry place away from direct sunlight.","Formulated without parabens, phthalates or mineral oil."}'),
('sports-outdoors', '{"Built and tested for real-world use on trails, roads and in the gym.","Inspect your gear before every outing and follow the included safety instructions.","Backed by the manufacturer''s warranty against defects in materials and workmanship."}'),
('toys-games', '{"Meets all applicable toy safety standards. Warning: small parts, not suitable for children under 3 unless stated otherwise.","Makes a great gift for birthdays and holidays.","Batteries, where required, are not included unless stated."}'),
('books', '{"Ships in protective packaging. Digital and audio editions are delivered to your library immediately after purchase.","Reading group guide available from the publisher.","Ideal for gifting: add a gift note at checkout."}'),
('grocery', '{"Store in a cool, dry place. Best enjoyed within the date printed on the package.","Packed in a facility that also handles nuts, milk and soy.","Sourced from growers who are paid fair prices for their harvest."}'),
('pet-supplies', '{"Transition to a new food gradually over 7-10 days.","Always supervise your pet with a new toy and replace it when worn.","Made with pets'' health and safety in mind and backed by a happiness guarantee."}'),
('automotive', '{"Check your vehicle''s make, model and year before ordering.","Easy installation with no tools required for most vehicles.","Covered by the manufacturer''s warranty."}'),
('garden-outdoor', '{"Built to withstand sun, rain and seasonal temperature swings.","Store in a dry place over winter to extend the life of the product.","Some assembly required; all hardware is included."}'),
('health-household', '{"These statements have not been evaluated by the Food and Drug Administration. This product is not intended to diagnose, treat, cure or prevent any disease.","Keep out of reach of children.","Made in a GMP-certified facility."}');

------------------------------------------------------------------------------------------------
-- lookup helpers
------------------------------------------------------------------------------------------------
create table seed.leaf_info as
select c.id as leaf_id, c.slug, c.path, r.slug as root,
       l.noun, l.pmin, l.pmax, l.qmax, l.weight, l.title, l.models, l.intro
from category c
join seed.leaf l on l.slug = c.slug
join category r on r.level = 0 and c.path like r.path || '%';
analyze seed.leaf_info;

-- attributes applying to each leaf (its own and its ancestors'), with category_attribute sort order
create table seed.leaf_attr as
select li.leaf_id, ca.id as ca_id, ca.code, ca.vals, ca.mult, ad.id as attribute_id, ad.data_type, ad.name as attr_name,
       ad.unit, row_number() over (partition by li.leaf_id order by anc.level, ca.id) as ord
from seed.leaf_info li
join category anc on li.path like anc.path || '%'
join seed.cat_attr ca on ca.cat = anc.slug
join attribute_definition ad on ad.code = ca.code;
analyze seed.leaf_attr;

create table seed.leaf_brands as
select leaf_id, array_agg(brand_id order by brand_id) as ids from seed.brand_leaf group by leaf_id;
analyze seed.leaf_brands;

-- sellers selling in each root, with cumulative size weights for weighted picking
create table seed.root_sellers as
select root, seller_id,
       (sum(size_weight) over (partition by root order by seller_id) / sum(size_weight) over (partition by root))::float8 as cum
from (
    select root, seller_id, size_weight from seed.seller_map
    union all
    select second_root, seller_id, size_weight * 0.5 from seed.seller_map where second_root is not null
) x;
analyze seed.root_sellers;
create index on seed.root_sellers (root, cum);

------------------------------------------------------------------------------------------------
-- 1. product skeletons
------------------------------------------------------------------------------------------------
create table seed.p as
with quota as (
    select leaf_id, root, round(${seed_products} * weight / sum(weight) over ())::int as n from seed.leaf_info
)
select row_number() over (order by q.leaf_id, g) as pid,
       q.leaf_id, q.root,
       random() as r_brand, random() as r_seller, random() as r_model, random() as r_price,
       random() as r_sale, random() as r_pop, random() as r_quality, random() as r_created,
       random() as r_status, random() as r_stock, random() as r_images
from quota q cross join generate_series(1, q.n) g;
analyze seed.p;

alter table seed.p add primary key (pid);
alter table seed.p add column brand_id bigint, add column seller_id bigint, add column model text,
    add column popularity numeric, add column quality numeric;

update seed.p p set
    brand_id = lb.ids[1 + floor(p.r_brand * array_length(lb.ids, 1))::int],
    model = seed.pick(li.models, p.r_model),
    -- popularity: heavy-tailed, used to pick products for orders
    popularity = round(power(p.r_pop, 3)::numeric * 100 + 0.5, 3),
    -- latent quality in [1.5, 5]: most products are good, some are poor (drives ratings and returns)
    quality = round((5 - 3.5 * power(p.r_quality, 2.2))::numeric, 2)
from seed.leaf_brands lb, seed.leaf_info li
where lb.leaf_id = p.leaf_id and li.leaf_id = p.leaf_id;

update seed.p p set seller_id = (
    select rs.seller_id from seed.root_sellers rs
    where rs.root = p.root and rs.cum >= p.r_seller
    order by rs.cum limit 1);

------------------------------------------------------------------------------------------------
-- 2. attribute values: index skewed towards the first (entry-level, most common) values
------------------------------------------------------------------------------------------------
create table seed.pv as
select p.pid, la.attribute_id, la.code, la.data_type, la.attr_name, la.unit, la.ord,
       x.idx, la.vals[x.idx] as val, coalesce(la.mult[x.idx], 1) as mult
from seed.p p
join seed.leaf_attr la on la.leaf_id = p.leaf_id
cross join lateral (
    select 1 + floor(power(random() + p.pid * 0, 1.35) * array_length(la.vals, 1))::int as idx
) x;

create index on seed.pv (pid);
analyze seed.pv;

alter table seed.pv add column label text;
update seed.pv v set label = case v.data_type
    when 'NUMBER' then seed.num_label(v.val::numeric)
    when 'BOOLEAN' then case v.val when 'true' then 'Yes' else 'No' end
    else (select o.label from attribute_option o where o.attribute_id = v.attribute_id and o.value = v.val)
end;

------------------------------------------------------------------------------------------------
-- 3. title, price, description
------------------------------------------------------------------------------------------------
create table seed.p_text as
select p.pid,
       -- collapse accidental repeats such as brand "Rosewood Studio" + model "Studio"
       regexp_replace(seed.render(li.title, vals), '(\m\w+) \1\M', '\1', 'gi') as title,
       regexp_replace(seed.render(li.intro, vals), '(\m\w+) \1\M', '\1', 'gi') as intro,
       exp(sum_ln) as mult
from seed.p p
join seed.leaf_info li on li.leaf_id = p.leaf_id
join seller s on s.id = p.seller_id
join brand b on b.id = p.brand_id
join lateral (
    select jsonb_object_agg(v.code, v.label)
               || jsonb_build_object('brand', b.name, 'model', p.model, 'seller', s.name) as vals,
           sum(ln(v.mult)) as sum_ln
    from seed.pv v where v.pid = p.pid
) a on true;
analyze seed.p_text;

create table seed.p_specs as
select v.pid,
       string_agg('- ' || v.attr_name || ': ' || v.label
                      || case when v.data_type = 'NUMBER' and v.unit is not null
                              then case when v.unit in ('%', '°F') then v.unit else ' ' || v.unit end
                              else '' end,
                  E'\n' order by v.ord) as specs
from seed.pv v
group by v.pid;
analyze seed.p_specs;

insert into product (id, seller_id, category_id, brand_id, sku, title, slug, description, price, list_price,
                     currency, status, rating_avg, rating_count, sales_count, created_at, updated_at)
select p.pid, p.seller_id, p.leaf_id, p.brand_id,
       upper(left(regexp_replace(b.name, '[^A-Za-z]', '', 'g'), 3)) || '-'
           || upper(left(regexp_replace(p.model, '[^A-Za-z]', '', 'g'), 3)) || '-' || lpad(p.pid::text, 6, '0'),
       t.title,
       left(seed.slugify(t.title), 300) || '-' || p.pid,
       t.intro || E'\n\nSpecifications:\n' || coalesce(sp.specs, '') || E'\n\n'
           || seed.pick(rt.paragraphs, p.r_sale) || E'\n\n' || seed.pick(rt.paragraphs, p.r_stock)
           || E'\n\nSold and shipped by ' || s.name || '.',
       price.v,
       case when p.r_sale < 0.22 then seed.retail_price((price.v * (1.1 + p.r_sale))::numeric) end,
       'USD',
       case when s.status <> 'ACTIVE' then 'DELISTED'
            when p.r_status < 0.02 then 'DRAFT'
            when p.r_status < 0.05 then 'DELISTED'
            else 'ACTIVE' end,
       null, 0, 0,
       created.v, created.v
from seed.p p
join seed.p_text t on t.pid = p.pid
left join seed.p_specs sp on sp.pid = p.pid
join seed.leaf_info li on li.leaf_id = p.leaf_id
join seed.root_text rt on rt.root = p.root
join seller s on s.id = p.seller_id
join brand b on b.id = p.brand_id
cross join lateral (select seed.retail_price(((li.pmin + (li.pmax - li.pmin) * p.r_price::numeric) * t.mult)::numeric) as v) price
cross join lateral (
    select s.joined_at + power(p.r_created, 2) * ((select now_ts from seed.config) - interval '30 days' - s.joined_at) as v
) created
order by p.pid;

select setval(pg_get_serial_sequence('product', 'id'), (select max(id) from product));

insert into product_attribute_value (product_id, attribute_id, option_id, number_value, bool_value)
select v.pid, v.attribute_id,
       case when v.data_type in ('ENUM', 'MULTI_ENUM') then o.id end,
       case when v.data_type = 'NUMBER' then v.val::numeric end,
       case when v.data_type = 'BOOLEAN' then v.val::boolean end
from seed.pv v
left join attribute_option o on o.attribute_id = v.attribute_id and o.value = v.val
order by v.pid, v.ord;

------------------------------------------------------------------------------------------------
-- 4. images (placeholder photos, stable per SKU) and inventory
------------------------------------------------------------------------------------------------
insert into product_image (product_id, url, alt_text, sort_order)
select pr.id, 'https://picsum.photos/seed/' || lower(pr.sku) || '-' || k || '/800/800',
       pr.title || case when k = 1 then '' else ' - view ' || k end, k
from product pr
join seed.p p on p.pid = pr.id
cross join lateral generate_series(1, 1 + floor(p.r_images * 4)::int) k;

insert into inventory (product_id, qty_available, qty_reserved, warehouse_code, restock_eta)
select pr.id,
       case when p.r_stock < 0.06 then 0 else (5 + floor(p.r_stock * 40 + p.popularity * 4))::int end,
       case when p.r_stock < 0.06 then 0 else floor(p.r_stock * 5)::int end,
       case sm.country when 'US' then seed.pick('{NJ-01,TX-02,CA-03,IL-01}'::text[], p.r_images)
                       when 'GB' then 'UK-LON1' when 'DE' then 'DE-BER1' else 'CA-TOR1' end,
       case when p.r_stock < 0.06 then ((select now_ts from seed.config)::date + (3 + floor(p.r_stock * 400))::int) end
from product pr
join seed.p p on p.pid = pr.id
join seed.seller_map sm on sm.seller_id = pr.seller_id;

-- sanity check: every title placeholder must have been rendered
do $$
begin
    if exists (select 1 from product where title like '%{%' or description like '%{%') then
        raise exception 'unrendered placeholder in product: %',
            (select title from product where title like '%{%' or description like '%{%' limit 1);
    end if;
end
$$;
