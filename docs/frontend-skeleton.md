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

A fixed shell: top bar (app name, global search, signed-in user with Sign out), a left
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
  store. The exceptions are `appFilter`, which binds to the list state the page provides, and `data-table` /
  `access-link`, which ask `AuthStore` whether a link may be shown.

URL parameters use the API names, so `/products?categoryId=46&brandIds=3&attr.ram_gb=16,32&sort=price_asc&page=1`
maps directly to `/api/products?...` (page is 0-based in both). Other screen state in the URL: the product review
list (`reviewRating`, `reviewSort`, `reviewPage`) and the document version (`version` or `at`). How far a ticket
thread has been loaded is screen state and stays out of the URL.

## Authentication

- `/login` (`features/auth/pages/login`) is outside the shell. Every other route is a child of the shell route, which
  `authGuard` protects. A signed-out user goes to `/login?returnUrl=…` and comes back to that page after signing in.
- `core/auth/AuthStore` (root):
  - keeps the token and its expiry in localStorage (`agenticshop.token`)
  - keeps the session from `/api/auth/me`: profile, role, seller and permissions
  - exposes `isLoggedIn` and `can(permission)`
- On app start, `provideAppInitializer` loads the session for a stored token, so a reload stays signed in and guards
  know the user. A stored token the backend rejects is dropped.
- `authInterceptor` adds `Authorization: Bearer` to `/api` calls. A 401 on any call except login signs the user out
  and sends them to `/login` with the current URL as `returnUrl`.
- The top bar shows the user, their role badge and Sign out.

### What the user sees

The backend enforces access; the UI only hides what would fail. Everything asks `AuthStore`:

- `core/auth/access.ts`: `AREA_PERMISSIONS` maps each top-level path (`orders`, `flags`…) to its permission. It is
  the single source for the guard, the nav and the links.
- `canOpen(commands | url)`: whether a link would open. It checks the area permission, and a seller user may only open
  their own seller's page.
- Routes:
  - Each area route has `canMatch: [permissionGuard]`.
  - Detail-page tabs that need more set `data: { permission }`: seller orders, contracts, payouts and flags, and
    customer orders, tickets and flags.
  - A denied route shows the "No access" page (`/forbidden`).
- Side nav: shows only the items `canOpen` allows, and hides empty groups.
- Detail tabs: the customer and seller frames filter their tabs by the same permissions.
- `SellerDetailStore` only loads contracts and payouts with `SELLER_FINANCE_READ`. The overview's fee card and
  "Last payout" tile are hidden without it.
- Cross-links:
  - `app-access-link` (`shared/ui/access-link`) renders a router link when `canOpen` allows it, and plain text
    otherwise.
  - `data-table` does the same for column links.
  - These two and `appFilter` are the shared UI pieces that read app state.
- Filters: list pages hide the customer filter for customers and the seller filter for seller users, since the
  backend scopes those anyway. The ticket assignee filter needs `USERS_READ`.
- `*appCan="'FLAGS_READ'"` (`core/auth/can.directive.ts`) shows an element only with a permission.
- The top-bar search only routes to screens the user can open. Anything else becomes a product search.

## Known gaps

- Seller filters take a seller ID rather than a dropdown (there are ~500 sellers); the ticket assignee dropdown
  lists the Tier 1 and Tier 2 agents.
- A URL with an invalid filter (e.g. an unknown `attr.` code) shows the backend's 400 message with Retry, which cannot
  fix it.
- Markdown tables in documents are not rendered; tablet and phone widths are unchecked.
