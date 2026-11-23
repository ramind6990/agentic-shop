import { ChangeDetectionStrategy, Component, input } from '@angular/core';

import { PageHeader } from '../page-header/page-header';

/** Stand-in for screens not built yet, and the not-found page. `title` and `message` come from route data. */
@Component({
  selector: 'app-not-built',
  imports: [PageHeader],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <app-page-header [title]="title()" />
    <div class="panel p-4 text-body-secondary">{{ message() }}</div>
  `,
})
export class NotBuilt {
  readonly title = input('Page not found');
  readonly message = input('This screen is not built yet.');
}
