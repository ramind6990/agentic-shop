# AgenticShop frontend: screens and data layer

The Angular app in `frontend/` is an admin-style console over the read-only backend API
(see `base-platform-data-and-api.md`). Every screen loads its data from the API; there is no mock data left.
Filters, sorting and paging are kept in the URL.

- Angular 21.2 (standalone components, zoneless, OnPush), Bootstrap 5.3 + Sass. Pinned to 21 because the dev
  machine runs Node 22.20; Angular 22 needs 22.22.3+.
- Run: `cd frontend && npm start` (http://localhost:4200). `/api` is proxied to `http://localhost:8080`
  (`proxy.conf.json`), so the backend needs no CORS setup. Postgres and the backend must be running.
- Conventions: `frontend/CLAUDE.md` and `frontend/.claude/rules/angular-best-practices.md`.

## Layout

A fixed shell: top bar (app name, global search, a "View as" role selector reserved for the auth phase), a left
nav grouped by area, and the routed page. List screens and the split screens (product search, ticket thread,
documents) fill the window and only their results pane scrolls; detail pages scroll as a whole.

| Area | Screens |
|---|---|
| Catalog | Product search (facet sidebar + card grid), product detail (gallery, specs, reviews), categories tree, brands |
| Sales | Orders, order detail (lines, returns, refunds, totals, payments, shipments), returns, return detail, refunds |
| People | Customers and sellers, each with a detail page and tabs as child routes; staff users |
| Support | Tickets, ticket thread (customer/staff/seller/system messages, internal notes, "load newer messages") |
| Risk | Account flags |
| Knowledge | Documents: list + viewer with version picker, "in force on" date, per-section minimum role, version history |

The global search in the top bar routes by what is typed: an order number (`AS-…`) opens the order, a ticket number
(`TCK-…`) searches tickets, an email searches customers, anything else searches products.

## Code layout (`frontend/src`)

```
styles/              global Sass: _variables (Bootstrap overrides, app tokens), _layout (shell, page-fill/scroll-pane),
                     _base (panel, section-label, kv-list, table links), _abstracts (for component @import)
app/core/
  http/              toHttpParams, toApiError (ProblemDetail → message), dayStart/dayAfter (date filters → instants)
  meta/              MetaStore: /api/meta/enums, loaded once, dropdown options for every enum
app/layout/          shell, top bar (global search), side nav (+ nav-items.ts)
app/shared/
  data/              loadOnChange, list-query (URL-backed list filters), paged-loader (detail-page tabs), url-query
  models/            PageResponse, Ref, Address (shapes shared by every endpoint)
  ui/                list-layout, data-table, pager, load-state, paged-section, filter-control (appFilter),
                     not-found-panel, page-header, section-card, entity-header, tab-nav, stat-tiles, status-badge,
                     role-badge, price, star-rating, breadcrumbs, address-block, filter-section, rich-text, not-built
  utils/             enumLabel / enumOptions
app/features/<area>/
  data-access/       <Thing>Api: HttpClient calls only, returns Observables, no state. providedIn root.
  models/            TypeScript mirrors of the backend DTOs; catalog also has product-query.ts (URL ↔ API mapping)
  state/             stores: page stores (provided by a page) and, where needed, root caches
  components/        area-specific pieces (e.g. order-table, ticket-table, flag-table, product-card)
  pages/             routed screens
  <area>.routes.ts   lazy-loaded routes
```

Features: `catalog`, `orders`, `returns` (returns + refunds), `customers`, `sellers`, `staff`, `support`, `risk`,
`documents`. Global styles hold only what several screens share; everything else lives in the component.

## Data layer

How data moves:

1. **The URL is the source of truth** for filters, sort and paging. A store reads it as a signal
   (`injectQueryParams()`) and derives its query with `computed`, compared by content so an equal URL causes no
   refetch.
2. **Loading**: `loadOnChange(request, r => api.call(r))` runs an effect that subscribes to the API call whenever
   `request` changes and writes the response into `value` / `loading` / `error` signals. The effect cleanup
   unsubscribes the previous call, so a slow stale response can never overwrite a newer one. `reload()` runs the
   same request again (Retry buttons).
3. **Rendering**: templates read the store's signals; OnPush + signals re-render only what changed.
4. **Actions** (`toggleBrand`, `setPage`, a filter change…) never set signals directly. They only write the URL
   (`injectQueryUpdater()`), which flows back through step 1. So a typed or pasted URL, the back button, a link and
   a click all go through the same path, and the URL always matches what is shown.

Building blocks:

- **List screens**: the store calls `injectListQuery(['q', 'status', …])`, which gives `filters`, `query` (filters +
  page), `setFilter`, `setPage`, `clear` and `hasFilters`, all backed by the URL. The page provides the store with
  `provideListState(Store)`, and each filter control just names its URL parameter: `<select appFilter="status">`.
  `appFilter` shows the URL value (also once async options such as enums have loaded) and writes changes back;
  Enter applies a text filter. `app-list-layout` takes the store's `result` / `loading` / `error` and emits
  `pageChange` / `retry`.
- **Detail pages**: the store reads the id from the path (`injectRouteParams()`), loads with `loadOnChange`, and
  exposes `notFound` for a 404, shown with `app-not-found-panel`.
- **Detail-page tabs** (customer, seller): the page frame provides its store (`CustomerDetailStore`,
  `SellerDetailStore`), so the tabs, rendered in its `<router-outlet>`, inject it to know the customer or seller.
  Each tab with its own list provides a small tab store built with `pagedLoader(owner, (id, page) => api…)`, with the
  page in the URL, and shows it in `app-paged-section`.
- **Date filters** are kept as `yyyy-mm-dd` in the URL and sent as instants for the start of that day and of the
  next day, in the browser's time zone (the zone the tables show times in).

Scopes:

- API services are stateless root singletons, reusable by any feature (the seller tabs use `ProductApi`,
  `OrderApi` and `FlagApi`).
- A page store is provided in the page component's `providers`: one instance per visit, destroyed on leave, shared
  with that page's feature components (`ProductFilters`, `ProductReviews`, `DocumentList`) without input/output
  chains.
- App-wide caches are root stores: `MetaStore` (enums for dropdowns) and `CategoryTreeStore` (the taxonomy, used by
  the category browser and the brands category filter).
- Shared UI components (`pager`, `data-table`, `list-layout`…) stay presentational: inputs and outputs only, never a
  store. The one exception is `appFilter`, which binds to the list state the page provides.

URL parameters use the API names, so `/products?categoryId=46&brandIds=3&attr.ram_gb=16,32&sort=price_asc&page=1`
maps directly to `/api/products?...` (page is 0-based in both). Other screen state in the URL: the product review
list (`reviewRating`, `reviewSort`, `reviewPage`) and the document version (`version` or `at`). How far a ticket
thread has been loaded is screen state and stays out of the URL.

## Known gaps

- Seller filters take a seller ID rather than a dropdown (there are ~500 sellers); the ticket assignee dropdown
  lists the Tier 1 and Tier 2 agents.
- A URL with an invalid filter (e.g. an unknown `attr.` code) shows the backend's 400 message with Retry, which cannot
  fix it.
- Markdown tables in documents are not rendered; tablet and phone widths are unchecked.
