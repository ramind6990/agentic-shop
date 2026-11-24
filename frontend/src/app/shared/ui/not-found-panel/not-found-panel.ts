import { ChangeDetectionStrategy, Component, input } from '@angular/core';
import { RouterLink } from '@angular/router';

/** Shown by detail pages when the API answers 404 (or the id in the URL is invalid). */
@Component({
  selector: 'app-not-found-panel',
  imports: [RouterLink],
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: { class: 'd-block' },
  template: `
    <div class="panel p-4 text-center">
      <h1 class="h5">{{ what() }} not found</h1>
      <p class="text-body-secondary">Nothing matches the id in the address bar.</p>
      <a [routerLink]="backLink()" class="btn btn-sm btn-outline-secondary">{{ backLabel() }}</a>
    </div>
  `,
})
export class NotFoundPanel {
  /** "Order", "Customer"… */
  readonly what = input.required<string>();
  readonly backLink = input.required<string>();
  readonly backLabel = input('Back to the list');
}
