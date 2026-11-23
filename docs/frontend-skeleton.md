# AgenticShop frontend: skeleton and data layer

The Angular app in `frontend/` is an admin-style console over the read-only backend API
(see `base-platform-data-and-api.md`). Every screen is built and styled. The catalog product pages (search and
detail) are wired to the API; the other screens still run on **mock data** and their filters, pagers and buttons do
nothing yet.

- Angular 21.2 (standalone components, zoneless, OnPush), Bootstrap 5.3 + Sass. Pinned to 21 because the dev
  machine runs Node 22.20; Angular 22 needs 22.22.3+.
- Run: `cd frontend && npm start` (http://localhost:4200). `/api` is proxied to `http://localhost:8080`
  (`proxy.conf.json`), so the backend needs no CORS setup once screens are wired.
- Conventions: `frontend/CLAUDE.md` and `frontend/.claude/rules/angular-best-practices.md`.

## Layout

A fixed shell: top bar (app name, global search, a "View as" role selector reserved for the auth phase), a left
nav grouped by area, and the routed page. List screens and the split screens (product search, ticket thread,
documents) fill the window and only their results pane scrolls; detail pages scroll as a whole.

| Area | Screens |
|---|---|
| Catalog | Product search (facet sidebar + card grid), product detail (gallery, specs, reviews), categories tree, brands |
| Sales | Orders, order detail (lines, returns, refunds, totals, payments, shipments), returns, refunds |
| People | Customers and sellers, each with a detail page and tabs as child routes; staff users |
| Support | Tickets, ticket thread (customer/staff/seller/system messages, internal notes) |
| Risk | Account flags |
| Knowledge | Documents: list + viewer with version picker, "in force on" date, per-section minimum role, version history |

Still a placeholder: return detail (`/returns/:id`).

## Code layout (`frontend/src`)

```
styles/            global Sass: _variables (Bootstrap overrides, app tokens), _layout (shell, page-fill/scroll-pane),
                   _base (panel, section-label, kv-list, table links), _abstracts (for component @import)
app/layout/        shell, top bar, side nav (+ nav-items.ts)
app/shared/
  models/          PageResponse, Ref, Address (shapes shared by every endpoint)
  ui/              list-layout, data-table (column definitions), pager, page-header, section-card, entity-header,
                   tab-nav, stat-tiles, status-badge, role-badge, price, star-rating, breadcrumbs, address-block,
                   filter-section, rich-text, not-built
  utils/           enumLabel / enumOptions
  mocks/           reference.mock.ts (people and sellers reused across mock lists)
app/features/<area>/
  models/          TypeScript mirrors of the backend DTOs and enums
  mocks/           mock data for that area
  components/      area-specific pieces (e.g. order-table, ticket-table, flag-table, product-card)
  pages/           routed screens
  <area>.routes.ts lazy-loaded routes
```

Features: `catalog`, `orders`, `returns` (returns + refunds), `customers`, `sellers`, `staff`, `support`, `risk`,
`documents`. Global styles hold only what several screens share; everything else lives in the component.

## Data layer (wired screens)

Wired so far: product search (`/products`) and product detail (`/products/:id`).

```
app/core/http/          toHttpParams (drops empty values, joins arrays with commas), toApiError (ProblemDetail → message)
app/shared/data/        loadOnChange (signal-driven loader), url-query (query params as a signal, URL updater, parsers)
app/shared/ui/          load-state (spinner / error + Retry / dimmed while reloading), pager emits pageChange
app/features/<area>/
  data-access/          <Thing>Api: HttpClient calls only, returns Observables, no state. providedIn root.
  models/<x>-query.ts   query type + pure functions: parse from ParamMap, map to API params
  state/<page>.store.ts @Injectable() provided by the page component; holds the page's signals and actions
```

How data moves:

1. **The URL is the source of truth** for filters, sort and paging. A store reads it with `injectQueryParams()` and
   derives `query = computed(() => parse(params))` (compared by content, so equal URLs cause no refetch).
2. **Loading**: `loadOnChange(query, q => api.search(q))` runs an effect that subscribes to the API call whenever
   `query` changes and writes the response into `value` / `loading` / `error` signals. The effect cleanup
   unsubscribes the previous call, so a slow stale response can never overwrite a newer one.
3. **Rendering**: templates read the store's signals; OnPush + signals re-render only what changed.
4. **Actions** (`toggleBrand`, `setPage`…) never set signals directly. They only write the URL
   (`injectQueryUpdater()`), which flows back through step 1. So a typed or pasted URL, the back button, a link and
   a click all go through the same path, and the URL always matches what is shown.

Scopes:

- API services are stateless root singletons, reusable by any feature (e.g. a seller's products tab can use
  `ProductApi`).
- A page store is provided in the page component's `providers`: one instance per visit, destroyed on leave, and
  injected by that page's feature components (`ProductFilters`, `ProductReviews`) without input/output chains.
- Shared UI components (`pager`, `star-rating`…) stay presentational: inputs and outputs only, never a store.
- App-wide cached data (enums from `/api/meta/enums`, the category tree) will get root stores when a screen needs it.

URL parameters use the API names, so `/products?categoryId=104&brandIds=3&attr.ram_gb=16,32&sort=price_asc&page=1`
maps directly to `/api/products?...` (page is 0-based in both). The detail page keeps its review list state in
`reviewRating`, `reviewSort` and `reviewPage`.

## Mock data

- Mocks sit in each feature's `mocks/` folder and match the backend response shapes (`PageResponse<T>`, DTO
  field names), so they can be swapped for API calls without touching templates.
- Content follows the backend seed: same naming, statuses that fit the timeline, fee rates from the seller fee
  schedule, policy and SOP text from `db/seed/V108`. Detail pages show one fixed record whatever the id in the URL.
- One storyline links several screens: customer Jordan King's lost UPS parcel (ticket TCK-048213, escalated to
  Tier 2), the matching refund, and an active refund-abuse flag.

## Next

1. Apply the catalog pattern (API service, URL-driven page store, `loadOnChange`) to the other features and delete
   their mocks (`catalog/mocks/product-search.mock.ts` is still used by the seller products tab).
2. Close the gaps: return detail, brand filter links, markdown tables in documents, check tablet and phone widths.
