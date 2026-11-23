import { DecimalPipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, input } from '@angular/core';
import { RouterLink, RouterLinkActive } from '@angular/router';

export interface TabLink {
  label: string;
  /** Child route path relative to the page, '' for the default tab. */
  path: string;
  count?: number;
}

/** Underlined tabs that switch between child routes of a detail page. */
@Component({
  selector: 'app-tab-nav',
  imports: [DecimalPipe, RouterLink, RouterLinkActive],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <nav class="mb-3" [attr.aria-label]="label()">
      <ul class="nav nav-underline flex-nowrap overflow-x-auto">
        @for (tab of tabs(); track tab.path) {
          <li class="nav-item">
            <a
              class="nav-link text-nowrap"
              [routerLink]="tab.path || './'"
              routerLinkActive="active"
              [routerLinkActiveOptions]="{ exact: tab.path === '' }"
              ariaCurrentWhenActive="page"
            >
              {{ tab.label }}
              @if (tab.count !== undefined) {
                <span class="badge rounded-pill text-bg-light border ms-1 num">{{ tab.count | number }}</span>
              }
            </a>
          </li>
        }
      </ul>
    </nav>
  `,
})
export class TabNav {
  readonly tabs = input.required<TabLink[]>();
  readonly label = input('Sections');
}
