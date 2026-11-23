import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';

import { StatusBadge } from '../status-badge/status-badge';

/**
 * Header for a person or company page: initials avatar, name, status and a short meta line.
 * Projected content goes on the right (key facts, links).
 */
@Component({
  selector: 'app-entity-header',
  imports: [StatusBadge],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <header class="d-flex flex-wrap align-items-center gap-3 mb-3">
      <div class="avatar" aria-hidden="true">{{ initials() }}</div>
      <div class="me-auto">
        <div class="d-flex flex-wrap align-items-center gap-2">
          <h1 class="h4 mb-0">{{ name() }}</h1>
          @if (status(); as s) {
            <app-status-badge [status]="s" />
          }
        </div>
        @if (meta()) {
          <p class="small text-body-secondary mb-0">{{ meta() }}</p>
        }
      </div>
      <ng-content />
    </header>
  `,
  styleUrl: './entity-header.scss',
})
export class EntityHeader {
  readonly name = input.required<string>();
  readonly status = input<string>();
  readonly meta = input<string>();

  protected readonly initials = computed(() =>
    this.name()
      .split(/\s+/)
      .filter((word) => /^[A-Za-z]/.test(word))
      .slice(0, 2)
      .map((word) => word[0].toUpperCase())
      .join(''),
  );
}
