import { ChangeDetectionStrategy, Component, input } from '@angular/core';

/** Titled block inside a filter sidebar. Reused by product search and the list pages. */
@Component({
  selector: 'app-filter-section',
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: { class: 'd-block py-3 border-bottom' },
  template: `
    <fieldset>
      <legend class="section-label mb-2">{{ title() }}</legend>
      <ng-content />
    </fieldset>
  `,
})
export class FilterSection {
  readonly title = input.required<string>();
}
