import { ChangeDetectionStrategy, Component, computed, inject } from '@angular/core';
import { RouterLink, RouterLinkActive } from '@angular/router';

import { AuthStore } from '../../core/auth/auth.store';
import { NAV_GROUPS } from './nav-items';

@Component({
  selector: 'app-side-nav',
  imports: [RouterLink, RouterLinkActive],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <nav class="py-3 px-2" aria-label="Main">
      @for (group of groups(); track group.label) {
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
  private readonly auth = inject(AuthStore);

  /** Only the screens this user may open; a group with none left is hidden. */
  protected readonly groups = computed(() =>
    NAV_GROUPS.map((group) => ({
      ...group,
      items: group.items.filter((item) => this.auth.canOpen(item.path)),
    })).filter((group) => group.items.length > 0),
  );
}
