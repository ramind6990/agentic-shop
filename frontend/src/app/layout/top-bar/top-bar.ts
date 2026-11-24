import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { Router, RouterLink } from '@angular/router';

import { AuthStore } from '../../core/auth/auth.store';
import { RoleBadge } from '../../shared/ui/role-badge/role-badge';

@Component({
  selector: 'app-top-bar',
  imports: [RouterLink, RoleBadge],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="d-flex align-items-center gap-3 h-100 px-3">
      <a routerLink="/" class="fw-semibold text-decoration-none text-body fs-5 me-2">AgenticShop</a>

      <form
        class="flex-grow-1"
        role="search"
        style="max-width: 32rem"
        (submit)="search($event, globalSearch)"
      >
        <label for="global-search" class="visually-hidden"
          >Search orders, tickets, products or customers</label
        >
        <input
          #globalSearch
          id="global-search"
          type="search"
          class="form-control form-control-sm"
          placeholder="Order number, ticket number, product or customer"
        />
      </form>

      @if (auth.session(); as user) {
        <div class="ms-auto d-flex align-items-center gap-2">
          <span class="small text-nowrap text-truncate" [title]="user.email">
            {{ user.fullName }}
            @if (user.seller) {
              <span class="text-body-secondary">� {{ user.seller.name }}</span>
            }
          </span>
          <app-role-badge [role]="user.role" />
          <button type="button" class="btn btn-sm btn-outline-secondary" (click)="auth.logout()">
            Sign out
          </button>
        </div>
      }
    </div>
  `,
})
export class TopBar {
  private readonly router = inject(Router);
  protected readonly auth = inject(AuthStore);

  /**
   * Sends the text to the screen that can answer it: an order number opens that order, a ticket number the
   * ticket search, an email the customer search, anything else the product search.
   */
  protected search(event: Event, input: HTMLInputElement): void {
    event.preventDefault();
    const text = input.value.trim();
    if (!text) {
      return;
    }
    if (/^AS-\d{6}-\d{6}$/i.test(text) && this.auth.can('ORDERS_READ')) {
      void this.router.navigate(['/orders', text.toUpperCase()]);
    } else if (/^TCK-/i.test(text) && this.auth.can('TICKETS_READ')) {
      void this.router.navigate(['/tickets'], { queryParams: { q: text.toUpperCase() } });
    } else if (text.includes('@') && this.auth.can('CUSTOMERS_READ')) {
      void this.router.navigate(['/customers'], { queryParams: { q: text } });
    } else {
      void this.router.navigate(['/products'], { queryParams: { q: text } });
    }
    input.value = '';
  }
}
