import { ChangeDetectionStrategy, Component } from '@angular/core';
import { RouterLink, RouterLinkActive } from '@angular/router';

import { NAV_GROUPS } from './nav-items';

@Component({
  selector: 'app-side-nav',
  imports: [RouterLink, RouterLinkActive],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <nav class="py-3 px-2" aria-label="Main">
      @for (group of groups; track group.label) {
        <div class="mb-3">
          <div class="section-label px-2 mb-1" [id]="'nav-' + group.label">{{ group.label }}</div>
          <ul class="nav flex-column" [attr.aria-labelledby]="'nav-' + group.label">
            @for (item of group.items; track item.path) {
              <li class="nav-item">
                <a
                  class="nav-link"
                  [routerLink]="item.path"
                  routerLinkActive="active"
                  ariaCurrentWhenActive="page"
                  >{{ item.label }}</a
                >
              </li>
            }
          </ul>
        </div>
      }
    </nav>
  `,
  styleUrl: './side-nav.scss',
})
export class SideNav {
  protected readonly groups = NAV_GROUPS;
}
