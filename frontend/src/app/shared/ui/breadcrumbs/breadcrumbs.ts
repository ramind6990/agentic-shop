import { ChangeDetectionStrategy, Component, input } from '@angular/core';
import { Params, RouterLink } from '@angular/router';

export interface Crumb {
  label: string;
  /** Router link; omit for the current (last) crumb. */
  link?: string | (string | number)[];
  queryParams?: Params;
}

@Component({
  selector: 'app-breadcrumbs',
  imports: [RouterLink],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <nav aria-label="Breadcrumb">
      <ol class="breadcrumb small mb-2">
        @for (crumb of items(); track $index; let last = $last) {
          <li
            class="breadcrumb-item"
            [class.active]="last"
            [attr.aria-current]="last ? 'page' : null"
          >
            @if (crumb.link && !last) {
              <a [routerLink]="crumb.link" [queryParams]="crumb.queryParams">{{ crumb.label }}</a>
            } @else {
              {{ crumb.label }}
            }
          </li>
        }
      </ol>
    </nav>
  `,
})
export class Breadcrumbs {
  readonly items = input.required<Crumb[]>();
}
