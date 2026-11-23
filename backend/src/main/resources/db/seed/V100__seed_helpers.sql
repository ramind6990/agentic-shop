-- Seed infrastructure: a staging schema (dropped by the last seed migration) and helper functions.
-- All seed data is deterministic: every seed migration starts with setseed(...).
-- "Now" for the fake data is fixed so that re-seeding produces the same dataset.

create schema if not exists seed;

create table seed.config (
    now_ts      timestamptz not null,
    history_start date      not null
);
insert into seed.config values ('2026-11-20 12:00:00+00', '2023-12-01');

-- random element of an array (pass a random number so callers control evaluation per row)
create function seed.pick(arr anyarray, r double precision) returns anyelement
    language sql immutable as
$$ select arr[1 + floor(r * array_length(arr, 1))::int] $$;

-- weighted pick: weights parallel to vals
create function seed.wpick(vals text[], weights numeric[], r double precision) returns text
    language plpgsql immutable as
$$
declare
    total numeric := 0;
    acc   numeric := 0;
    i     int;
begin
    for i in 1 .. array_length(weights, 1) loop
        total := total + weights[i];
    end loop;
    for i in 1 .. array_length(vals, 1) loop
        acc := acc + weights[i];
        if r * total < acc then
            return vals[i];
        end if;
    end loop;
    return vals[array_length(vals, 1)];
end
$$;

create function seed.slugify(t text) returns text
    language sql immutable as
$$ select trim(both '-' from regexp_replace(lower(translate(t, 'äöüßéè', 'aousee')), '[^a-z0-9]+', '-', 'g')) $$;

-- replaces {key} placeholders with values from a jsonb object
create function seed.render(tpl text, vals jsonb) returns text
    language plpgsql immutable as
$$
declare
    result text := tpl;
    k      text;
    v      text;
begin
    for k, v in select key, value from jsonb_each_text(vals) loop
        result := replace(result, '{' || k || '}', v);
    end loop;
    return result;
end
$$;

-- "nice" retail price: .99 endings above $10, .49/.99 below
create function seed.retail_price(x numeric) returns numeric
    language sql immutable as
$$
select case
           when x < 10 then greatest(floor(x) + 0.49, 2.99)
           when x < 100 then floor(x) + 0.99
           else round(x / 5) * 5 - 0.01
       end
$$;

-- label for a number: 15.60 -> 15.6, 16.00 -> 16
create function seed.num_label(n numeric) returns text
    language sql immutable as
$$ select case when n = trunc(n) then trunc(n)::text else rtrim(rtrim(n::text, '0'), '.') end $$;
