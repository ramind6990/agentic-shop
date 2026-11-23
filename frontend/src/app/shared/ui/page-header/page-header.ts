import { ChangeDetectionStrategy, Component, input } from '@angular/core';

/** Page title with an optional subtitle; projected content is placed on the right (actions, search, sort). */
@Component({
  selector: 'app-page-header',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="d-flex flex-wrap align-items-end gap-3 mb-3">
      <div class="me-auto">
        <h1 class="h4 mb-0">{{ title() }}</h1>
        @if (subtitle()) {
          <p class="text-body-secondary small mb-0">{{ subtitle() }}</p>
        }
      </div>
      <ng-content />
    </div>
  `,
})
export class PageHeader {
  readonly title = input.required<string>();
  readonly subtitle = input<string>();
}
