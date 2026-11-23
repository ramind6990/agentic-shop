---
paths:
  - "src/**"
---

# Angular best practices

You are an expert in TypeScript, Angular, and scalable web application development. You write functional,
maintainable, performant, and accessible code following Angular and TypeScript best practices.

This project is on **Angular 21.2** (it is pinned there because of the Node version; see `CLAUDE.md`). A few rules
below are adjusted for v21. When the project moves to v22, switch to the v22 variants noted in each rule.

## TypeScript

- Use strict type checking.
- Prefer type inference when the type is obvious.
- Avoid the `any` type; use `unknown` when the type is uncertain.

## Angular

- Always use standalone components over NgModules.
- Do NOT set `standalone: true` inside Angular decorators. It is the default since v20.
- Set `changeDetection: ChangeDetectionStrategy.OnPush` on every component. On v21 it is not the default yet.
  (From v22, OnPush is the default: stop setting it.)
- Use signals for state management.
- Lazy-load feature routes (`loadComponent` / `loadChildren`).
- Do NOT use the `@HostBinding` and `@HostListener` decorators. Put host bindings inside the `host` object of the
  `@Component` or `@Directive` decorator instead.
- Use `NgOptimizedImage` for all static images. It does not work for inline base64 images.

## Accessibility

- It MUST pass all AXE checks.
- It MUST follow all WCAG AA minimums, including focus management, color contrast, and ARIA attributes.

## Components

- Keep components small and focused on a single responsibility.
- Use `input()` and `output()` functions instead of decorators.
- Use `model()` for two-way bound properties with `[(prop)]` syntax instead of pairing `input()` with `output()`.
- Use `computed()` for derived state.
- Use `linkedSignal()` for state derived from multiple reactive sources that must stay synchronized.
- Prefer inline templates for small components.
- Forms: use Reactive forms, not Template-driven ones. Signal Forms (`@angular/forms/signals`) are still experimental
  in v21, so don't use them yet. (From v22 they are stable: prefer them for new forms.)
- Do NOT use `ngClass`; use `class` bindings instead.
- Do NOT use `ngStyle`; use `style` bindings instead.
- Do NOT import `CommonModule`. Import only the directives and pipes the template uses, such as `AsyncPipe` or
  `DatePipe`.
- When using external templates/styles, use paths relative to the component TS file.

## State management

- Use signals for local component state.
- Use `computed()` for derived state.
- Keep state transformations pure and predictable.
- Do NOT use `mutate` on signals; use `update` or `set` instead.

## Templates

- Keep templates simple and avoid complex logic.
- Use native control flow (`@if`, `@for`, `@switch`) instead of `*ngIf`, `*ngFor`, `*ngSwitch`.
- Use the async pipe to handle observables.
- Do not assume globals like `new Date()` are available in templates.

## Services

- Design services around a single responsibility.
- Use `@Injectable({ providedIn: 'root' })` for singleton services. The `@Service` decorator does not exist in v21.
  (From v22, prefer `@Service` for new singleton services.)
- Use the `inject()` function instead of constructor injection.
