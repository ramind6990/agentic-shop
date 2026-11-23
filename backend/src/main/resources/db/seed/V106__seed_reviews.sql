-- Reviews from verified purchases. Rating follows the product's latent quality (and the buyer's return, if any);
-- text comes from category-specific phrase banks matching the rating's sentiment. {noun} = e.g. "laptop".
select setseed(0.106);

-- planner statistics for the tables filled by previous seed steps
analyze;

create table seed.review_bank (
    root      text   not null,   -- '*' = any category
    sentiment text   not null,   -- pos / mid / neg
    sentences text[] not null
);

insert into seed.review_bank (root, sentiment, sentences) values
('*', 'pos', '{"Would definitely buy again.","Exactly as described and arrived quickly.","Great value for the price.","Highly recommend this seller.","Exceeded my expectations.","Five stars, no complaints at all."}'),
('*', 'mid', '{"It does the job, nothing special.","Decent for the price, but I expected a bit more.","Some pros and some cons.","Okay overall, would consider other options next time.","Not bad, not great."}'),
('*', 'neg', '{"Very disappointed.","Would not recommend.","Save your money.","Not worth the price at all.","Quality control seems to be lacking."}'),
('computers', 'pos', '{"The {noun} was easy to set up and worked right out of the box.","Fast, responsive and the screen is gorgeous.","Build quality feels premium for this price range.","Typing and tracking feel precise, a real upgrade for my desk.","Handles dozens of browser tabs without slowing down."}'),
('computers', 'mid', '{"The {noun} works, but the software feels a bit clunky.","Performance is okay for everyday tasks, it struggles with anything heavy.","Gets warm after an hour of use.","Decent, but the stand and cables feel cheap."}'),
('computers', 'neg', '{"The {noun} stopped working after two weeks.","Arrived with a dead pixel and the seller was slow to respond.","Gets extremely hot and shuts down by itself.","Keys started double-typing within a month.","Constant disconnects, very frustrating."}'),
('audio', 'pos', '{"Sound quality is crisp with plenty of bass.","Pairing was instant and the connection has been rock solid.","Battery life is excellent, easily lasts all day.","Comfortable even after hours of listening.","Noise cancelling works great on my commute."}'),
('audio', 'mid', '{"Sound is fine, but the bass is a bit muddy.","Battery life is fine but not as good as advertised.","Comfortable, but the controls are fiddly.","Connection drops now and then."}'),
('audio', 'neg', '{"One side stopped working after two weeks.","Constant connection drops, very frustrating.","Battery drains in a few hours, nowhere near the claimed runtime.","Tinny sound and no bass at all.","Hurts my ears after twenty minutes."}'),
('phones-tablets', 'pos', '{"The {noun} was easy to set up and worked right out of the box.","Battery life is excellent, easily lasts all day.","Charges fast and feels well made.","Fits perfectly and feels sturdy.","Great value compared to the big brands."}'),
('phones-tablets', 'mid', '{"The {noun} works, but it is slower than I hoped.","Okay, but it scratches easily.","Battery life is fine but not as good as advertised.","Fit is a little loose."}'),
('phones-tablets', 'neg', '{"The {noun} stopped working after two weeks.","Gets extremely hot while charging.","Battery drains in a few hours, nowhere near the claimed runtime.","Cracked after one small drop.","Does not charge at the advertised speed."}'),
('home-kitchen', 'pos', '{"The {noun} looks great in our kitchen and is easy to clean.","Heats evenly and nothing sticks.","Feels sturdy and well made, should last for years.","Super soft and comfortable, we bought a second one.","Assembly took five minutes and it looks more expensive than it was."}'),
('home-kitchen', 'mid', '{"The {noun} is fine, but smaller than I expected.","Works well but is harder to clean than I hoped.","Color is slightly different from the photos.","Decent quality, the finish scratches easily."}'),
('home-kitchen', 'neg', '{"The coating started peeling after a month.","The {noun} arrived with a crack.","Pilled after the first wash.","Flimsy, wobbles and feels cheap.","Smells strongly of chemicals even after washing."}'),
('fashion', 'pos', '{"Fits true to size and the fabric feels great.","Love the color, I get compliments every time I wear it.","Comfortable all day and washes well.","The cut is really flattering.","Great quality stitching, much better than fast fashion."}'),
('fashion', 'mid', '{"The {noun} runs a little small, size up.","Nice style but the fabric is thinner than expected.","Color faded a bit after a few washes.","Comfortable, but the fit is a bit boxy."}'),
('fashion', 'neg', '{"Runs at least two sizes small.","Seams came apart after one wear.","The fabric is see-through and scratchy.","Looks nothing like the photos.","Shrank badly after the first wash."}'),
('beauty', 'pos', '{"My skin feels so soft and hydrated.","Absorbs quickly and does not feel greasy.","A little goes a long way, this will last months.","Lovely subtle scent and no irritation at all.","My hair has never been this shiny."}'),
('beauty', 'mid', '{"Nice texture but I did not notice a big difference.","The scent is a bit strong for my taste.","Works okay, the packaging is hard to dispense from.","Fine for daily use, nothing amazing."}'),
('beauty', 'neg', '{"Broke me out within days.","Made my skin red and itchy.","The pump stopped working after a week.","Smells nothing like described and fades in minutes.","Left a greasy film on my face."}'),
('sports-outdoors', 'pos', '{"Held up perfectly on a week-long trip.","Solid build and very comfortable to use.","Light enough to carry all day.","Great grip and no slipping during workouts.","Set up in minutes, even in the wind."}'),
('sports-outdoors', 'mid', '{"Does the job, but heavier than I expected.","Good for casual use, not for serious training.","The {noun} is okay, the instructions were confusing.","Works fine, the zipper feels a bit cheap."}'),
('sports-outdoors', 'neg', '{"Leaked on the first rainy night.","The {noun} broke on the second use.","Much heavier than listed.","Coating started flaking off after a few weeks.","Not as warm as the rating claims at all."}'),
('toys-games', 'pos', '{"My kids played with it for hours.","Great family game, we play every weekend.","All pieces were there and the quality is excellent.","Perfect gift, my nephew loved it.","Clear instructions and a lot of fun to build."}'),
('toys-games', 'mid', '{"Fun at first but the kids got bored after a week.","Rules are a bit confusing for younger players.","Good, but smaller than it looks in the pictures.","Okay quality for the price."}'),
('toys-games', 'neg', '{"Missing several pieces.","Broke within the first day of play.","Cheap plastic with sharp edges.","The battery compartment cracked immediately.","Box was damaged and pieces were loose."}'),
('books', 'pos', '{"Could not put it down, finished it in two days.","Beautifully written with characters I really cared about.","One of the best books I have read this year.","Practical and full of ideas I could use right away.","The recipes are easy to follow and delicious."}'),
('books', 'mid', '{"Started strong but the middle dragged.","Good ideas, but could have been half as long.","Enjoyable, but the ending felt rushed.","Some great chapters and some forgettable ones."}'),
('books', 'neg', '{"Predictable plot and flat characters.","Arrived with a bent cover and torn pages.","Too repetitive, I gave up halfway.","Not what the description promised.","Poorly edited, full of typos."}'),
('grocery', 'pos', '{"Delicious and fresh, this is my new favorite.","Smooth, rich flavor without bitterness.","Arrived well packed with a long best-before date.","Great quality, you can taste the difference.","Perfect for everyday use."}'),
('grocery', 'mid', '{"Tastes fine but nothing special.","A bit pricey for the quantity.","Good, but I prefer a stronger flavor.","Decent, the packaging could be better."}'),
('grocery', 'neg', '{"Arrived stale.","The bag was torn open on arrival.","Tastes bitter and burnt.","Close to the expiry date when it arrived.","Not the flavor described at all."}'),
('pet-supplies', 'pos', '{"My dog absolutely loves it.","Our cat took to it immediately.","Sturdy and easy to clean.","Great ingredients and no tummy troubles.","Has held up to a very enthusiastic chewer."}'),
('pet-supplies', 'mid', '{"My pet is lukewarm about it.","Okay, but smaller than expected.","Works, but the smell is strong.","Fine, the cover is hard to put back on."}'),
('pet-supplies', 'neg', '{"Destroyed in ten minutes.","Made my dog sick.","My cat refuses to touch it.","Stuffing came out on the first day.","Smells awful and the bag was damaged."}'),
('automotive', 'pos', '{"Fits my car perfectly.","Installed in minutes and works great.","Video quality is sharp, even at night.","Holds my phone steady on bumpy roads.","Strong suction and very handy."}'),
('automotive', 'mid', '{"Works, but the fit is not perfect.","Okay, the app is a bit buggy.","Fine for the price, the mount loosens occasionally.","Decent, but louder than expected."}'),
('automotive', 'neg', '{"Does not fit as advertised.","Stopped recording after a week.","Falls off the dashboard constantly.","Weak suction, barely picks anything up.","The adhesive melted in the summer heat."}'),
('garden-outdoor', 'pos', '{"Survived a whole season outdoors and still looks new.","Sturdy and easy to assemble.","Cuts cleanly with very little effort.","Heats up quickly and cooks evenly.","Great quality for the price."}'),
('garden-outdoor', 'mid', '{"Okay, but the color faded over the summer.","Assembly took longer than expected.","Works fine, but feels a bit flimsy.","Decent, the cover does not fit well."}'),
('garden-outdoor', 'neg', '{"Rusted after a few weeks.","Leaks at the connector.","Broke in the first strong wind.","Missing hardware and impossible to assemble.","The paint peeled within a month."}'),
('health-household', 'pos', '{"Works great and lasts a long time.","Easy to take and no aftertaste.","Picks up everything, even pet hair.","Clothes come out fresh and clean.","Mixes well with no clumps."}'),
('health-household', 'mid', '{"Okay, but I did not notice much difference.","Works, but the battery could last longer.","Fine, the scent is a bit strong.","Decent value, the flavor is just okay."}'),
('health-household', 'neg', '{"Gave me stomach issues.","Stopped charging after a month.","Leaves residue on clothes.","Clumps and tastes chalky.","Gets stuck on every rug."}');

create table seed.review_titles (
    sentiment text   primary key,
    titles    text[] not null
);
insert into seed.review_titles values
('pos', '{"Love it!","Excellent quality","Exactly what I needed","Highly recommend","Great purchase","Fantastic","Worth every penny","Better than expected"}'),
('mid', '{"It''s okay","Decent but not perfect","Mixed feelings","Average","Does the job","Could be better"}'),
('neg', '{"Disappointed","Do not buy","Poor quality","Not as described","Returned it","Waste of money","Broke quickly"}');

-- candidates: delivered (or later refunded/returned) order lines
create table seed.rv as
select i.id as order_item_id, i.product_id, o.customer_id, s.delivered_at, li.root, grp.slug as grp, li.noun, p.quality,
       rr.reason as return_reason,
       random() as r_take, random() as r_noise1, random() as r_noise2, random() as r_noise3,
       random() as r_title, random() as r_s1, random() as r_s2, random() as r_s3, random() as r_time, random() as r_help
from order_item i
join customer_order o on o.id = i.order_id
join shipment s on s.order_id = o.id and s.status = 'DELIVERED'
join product pr on pr.id = i.product_id
join seed.leaf_info li on li.leaf_id = pr.category_id
join category leaf on leaf.id = pr.category_id
join category grp on grp.id = leaf.parent_id
join seed.p p on p.pid = pr.id
left join return_request rr on rr.order_item_id = i.id;
analyze seed.rv;

-- buyers with very good or very bad experiences review more often
delete from seed.rv
where r_take >= case when return_reason is not null then 0.6
                     else 0.35 + abs(quality - 3.5) * 0.08 end;

alter table seed.rv add column rating int, add column created_at timestamptz, add column sentiment text;

update seed.rv set
    rating = case
        when return_reason in ('DAMAGED', 'DEFECTIVE', 'NOT_AS_DESCRIBED', 'WRONG_ITEM') then 1 + floor(r_noise1 * 2)::int
        when return_reason is not null then 2 + floor(r_noise1 * 2)::int
        else greatest(1, least(5, round(quality + (r_noise1 + r_noise2 + r_noise3 - 1.5) * 1.1)))::int end,
    created_at = delivered_at + make_interval(secs => 86400 * (1 + power(r_time, 2) * 40));

delete from seed.rv where created_at > (select now_ts from seed.config);
update seed.rv set sentiment = case when rating >= 4 then 'pos' when rating = 3 then 'mid' else 'neg' end;

insert into review (product_id, customer_id, order_item_id, rating, title, body, helpful_count, created_at)
select v.product_id, v.customer_id, v.order_item_id, v.rating,
       seed.pick(t.titles, v.r_title),
       replace(
           seed.pick(cat.sentences, v.r_s1)
           || case when v.r_s3 < 0.6 then ' ' || seed.pick(cat.sentences, v.r_s3) else '' end
           || case when v.return_reason is not null
                   then ' ' || seed.pick('{"I ended up returning it.","Had to send it back.","Returned it for a refund."}'::text[], v.r_s3)
                   else '' end
           || ' ' || seed.pick(gen.sentences, v.r_s2),
           '{noun}', v.noun),
       floor(power(v.r_help, 6) * 120)::int,
       v.created_at
from seed.rv v
join seed.review_titles t on t.sentiment = v.sentiment
-- most specific phrase bank: the product's group (e.g. audio) if it has one, else its root category
join seed.review_bank cat on cat.sentiment = v.sentiment
    and cat.root = case when exists (select 1 from seed.review_bank b where b.root = v.grp) then v.grp else v.root end
join seed.review_bank gen on gen.root = '*' and gen.sentiment = v.sentiment
order by v.created_at;

-- remove the accidental duplicate sentence when both picks hit the same one
update review set body = regexp_replace(body, '^(.+?\.) \1', '\1');

------------------------------------------------------------------------------------------------
-- rating rollups
------------------------------------------------------------------------------------------------
update product pr set rating_avg = r.avg, rating_count = r.cnt
from (select product_id, round(avg(rating), 2) as avg, count(*)::int as cnt from review group by product_id) r
where r.product_id = pr.id;

update seller s set rating_avg = r.avg
from (select seller_id, round(sum(rating_avg * rating_count) / sum(rating_count), 2) as avg
      from product where rating_count > 0 group by seller_id) r
where r.seller_id = s.id;
