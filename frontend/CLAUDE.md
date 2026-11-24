# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What this is

Angular frontend for AgenticShop, a practice marketplace ("ShopOps") used to learn agentic systems. It presents the
data served by the Spring Boot backend in `../backend`. The backend is the source of truth for the domain; read
`../docs/base-platform-data-and-api.md` for the entities, seed data and every REST endpoint, and
`../docs/ShopOps_Agentic_Practice_Plan.pdf` for the overall roadmap. The base app is meant to stay simple: its job is
to show realistic data, not to be a polished product.

Current state: Angular 21.2 (standalone components, zoneless, SCSS, routing on, no SSR). Every screen loads its
data from the API (no mocks). The data-layer pattern (API services, URL-driven stores, `loadOnChange`,
`injectListQuery` + `appFilter`, `pagedLoader` for detail tabs) is described in `../docs/frontend-skeleton.md` under
"Data layer". Don't use `rxResource` / `httpResource` (experimental in v21); use `loadOnChange` from
`src/app/shared/data`.

## Commands

```
npm start          # ng serve on http://localhost:4200, proxies /api to the backend
npm run build      # production build into dist/agenticshop
npm run watch      # dev build in watch mode
npx ng generate component <path/name>
```

- Angular is pinned to 21.x because the dev machine runs Node 22.20, and Angular 22 needs Node 22.22.3 or later.
  Don't upgrade Angular without checking Node first.
- No tests yet: the app was generated with `--skip-tests` and has no test runner dependency, so `ng test` does not
  work. Tests are deferred for now.
- Formatting: Prettier (`.prettierrc`: single quotes, print width 100, Angular parser for HTML).

## Backend integration

- All API calls use relative `/api/...` URLs. `proxy.conf.json` (wired into `angular.json` under `serve.options`)
  forwards them to `http://localhost:8080`. The backend has no CORS config, so keep using the proxy rather than
  absolute URLs.
- The backend needs Postgres, which runs through Docker on another machine. Ask the user to run `docker compose up -d`
  and start the backend (`mvn spring-boot:run` in `../backend`) before checking anything that calls the API.
- API contract: Swagger UI at `http://localhost:8080/swagger-ui.html`, OpenAPI JSON at `/v3/api-docs`.
- Everything is read-only (`GET`) with no authentication yet. Roles and seller tenancy exist in the data but are not
  enforced, so the UI currently sees everything.
- Paged responses always have the shape `{ content, page, size, totalElements, totalPages }`. `page` starts at 0 and
  `size` is capped at 100. Errors are RFC 9457 ProblemDetail.
- `/api/meta/enums` returns every enum's values for dropdowns, so don't hard-code status lists.
- Product search filters use `attr.<code>=v1,v2` and `attr.<code>.min/.max`. `/api/products/facets` takes the same
  filters and returns disjunctive counts, and `/api/categories/{id}/filters` describes which attribute filters to
  show.
