import { DatePipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, computed, inject } from '@angular/core';
import { RouterOutlet } from '@angular/router';

import { AuthStore } from '../../../../core/auth/auth.store';
import { Permission } from '../../../../core/auth/auth.models';
import { Breadcrumbs, Crumb } from '../../../../shared/ui/breadcrumbs/breadcrumbs';
import { EntityHeader } from '../../../../shared/ui/entity-header/entity-header';
import { LoadState } from '../../../../shared/ui/load-state/load-state';
import { NotFoundPanel } from '../../../../shared/ui/not-found-panel/not-found-panel';
import { TabLink, TabNav } from '../../../../shared/ui/tab-nav/tab-nav';
import { CustomerDetailStore } from '../../state/customer-detail.store';

/**
 * Customer page frame: header and tabs; each tab is a child route rendered in the outlet.
 * Provides CustomerDetailStore, which the tabs inject to know which customer they show.
 */
@Component({
  selector: 'app-customer-detail',
  imports: [DatePipe, RouterOutlet, Breadcrumbs, EntityHeader, LoadState, NotFoundPanel, TabNav],
  providers: [CustomerDetailStore],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @if (store.notFound()) {
      <app-not-found-panel what="Customer" backLink="/customers" />
    } @else {
      <app-load-state
        [loading]="store.loading()"
        [error]="store.error()"
        [hasValue]="!!store.customer()"
        (retry)="store.reload()"
      >
        @if (store.customer(); as customer) {
          <app-breadcrumbs [items]="crumbs()" />
          <app-entity-header
            [name]="customer.fullName"
            [status]="customer.status"
            [meta]="customer.email"
          >
            <span class="small text-body-secondary">
              Customer since
              <time [attr.datetime]="customer.createdAt">{{
                customer.createdAt | date: 'mediumDate'
              }}</time>
            </span>
          </app-entity-header>
          <app-tab-nav label="Customer sections" [tabs]="tabs()" />
          <router-outlet />
        }
      </app-load-state>
    }
  `,
})
export class CustomerDetailPage {
  protected readonly store = inject(CustomerDetailStore);
  private readonly auth = inject(AuthStore);

  protected readonly crumbs = computed<Crumb[]>(() => [
    { label: 'Customers', link: '/customers' },
    { label: this.store.customer()?.fullName ?? '' },
  ]);

  /** Tabs the user may open (the routes have the same permissions). */
  protected readonly tabs = computed<TabLink[]>(() => {
    const customer = this.store.customer();
    const tabs: (TabLink & { permission?: Permission })[] = [
      { label: 'Overview', path: '' },
      { label: 'Addresses', path: 'addresses', count: customer?.addresses.length },
      {
        label: 'Orders',
        path: 'orders',
        count: customer?.stats.orderCount,
        permission: 'ORDERS_READ',
      },
      {
        label: 'Tickets',
        path: 'tickets',
        count: customer?.stats.ticketCount,
        permission: 'TICKETS_READ',
      },
      { label: 'Reviews', path: 'reviews' },
      {
        label: 'Flags',
        path: 'flags',
        count: customer?.stats.activeFlagCount,
        permission: 'FLAGS_READ',
      },
    ];
    return tabs.filter((tab) => !tab.permission || this.auth.can(tab.permission));
  });
}
