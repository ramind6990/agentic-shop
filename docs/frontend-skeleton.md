# AgenticShop frontend: skeleton with mock data

The Angular app in `frontend/` is an admin-style console over the read-only backend API
(see `base-platform-data-and-api.md`). At this stage every screen is built and styled, but it runs on
**mock data only**: no HTTP calls, services or stores yet, and filters, pagers and buttons do nothing.

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

## Mock data

- Mocks sit in each feature's `mocks/` folder and match the backend response shapes (`PageResponse<T>`, DTO
  field names), so they can be swapped for API calls without touching templates.
- Content follows the backend seed: same naming, statuses that fit the timeline, fee rates from the seller fee
  schedule, policy and SOP text from `db/seed/V108`. Detail pages show one fixed record whatever the id in the URL.
- One storyline links several screens: customer Jordan King's lost UPS parcel (ticket TCK-048213, escalated to
  Tier 2), the matching refund, and an active refund-abuse flag.

## Next

1. Wire one feature (orders) to the API: a service per feature using `HttpClient`, signal-based state for filters,
   paging and detail data. Then apply the same pattern to the other features and delete their mocks.
2. Make filters, sorting and pagers work, with state kept in the URL.
3. Close the gaps: return detail, brand filter links, markdown tables in documents, check tablet and phone widths.
