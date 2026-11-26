import { ChangeDetectionStrategy, Component, computed, inject } from '@angular/core';
import { RouterLink, RouterLinkActive } from '@angular/router';

import { AuthStore } from '../../core/auth/auth.store';
import { NAV_GROUPS } from './nav-items';

@Component({
  selector: 'app-side-nav',
  imports: [RouterLink, RouterLinkActive],
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: { class: 'd-block h-100' },
  template: `
    <nav class="py-3 px-2 d-flex flex-column min-h-100" aria-label="Main">
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
      @if (canChat()) {
        <a
          class="btn btn-primary support-chat mt-auto"
          routerLink="/chat"
          routerLinkActive="active"
          ariaCurrentWhenActive="page"
        >
          <svg width="16" height="16" viewBox="0 0 16 16" fill="currentColor" aria-hidden="true">
            <path d="M2 3.5A1.5 1.5 0 0 1 3.5 2h9A1.5 1.5 0 0 1 14 3.5v6a1.5 1.5 0 0 1-1.5 1.5H6.6l-2.85 2.38A.75.75 0 0 1 2.5 12.8V11A1.5 1.5 0 0 1 2 9.5v-6Z" />
          </svg>
          Support chat
        </a>
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

  /** The support chat (pinned at the bottom) for users who may talk to the assistant. */
  protected readonly canChat = computed(() => this.auth.isLoggedIn() && this.auth.can('AI_CHAT'));
}
