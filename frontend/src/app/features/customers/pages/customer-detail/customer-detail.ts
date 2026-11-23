import { DatePipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, input } from '@angular/core';
import { RouterOutlet } from '@angular/router';

import { Breadcrumbs, Crumb } from '../../../../shared/ui/breadcrumbs/breadcrumbs';
import { EntityHeader } from '../../../../shared/ui/entity-header/entity-header';
import { TabLink, TabNav } from '../../../../shared/ui/tab-nav/tab-nav';
import { flagsFor } from '../../../risk/mocks/flags.mock';
import { MOCK_CUSTOMER, MOCK_CUSTOMER_REVIEWS } from '../../mocks/customer-detail.mock';

/** Customer page frame: header and tabs; each tab is a child route rendered in the outlet. */
@Component({
  selector: 'app-customer-detail',
  imports: [DatePipe, RouterOutlet, Breadcrumbs, EntityHeader, TabNav],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <app-breadcrumbs [items]="crumbs" />
    <app-entity-header [name]="customer.fullName" [status]="customer.status" [meta]="customer.email">
      <span class="small text-body-secondary">
        Customer since
        <time [attr.datetime]="customer.createdAt">{{ customer.createdAt | date: 'mediumDate' }}</time>
      </span>
    </app-entity-header>
    <app-tab-nav label="Customer sections" [tabs]="tabs" />
    <router-outlet />
  `,
})
export class CustomerDetailPage {
  /** Route parameter; unused until the API is wired. */
  readonly id = input<string>();

  protected readonly customer = MOCK_CUSTOMER;
  protected readonly crumbs: Crumb[] = [
    { label: 'Customers', link: '/customers' },
    { label: this.customer.fullName },
  ];

  protected readonly tabs: TabLink[] = [
    { label: 'Overview', path: '' },
    { label: 'Addresses', path: 'addresses', count: this.customer.addresses.length },
    { label: 'Orders', path: 'orders', count: this.customer.stats.orderCount },
    { label: 'Tickets', path: 'tickets', count: this.customer.stats.ticketCount },
    { label: 'Reviews', path: 'reviews', count: MOCK_CUSTOMER_REVIEWS.length },
    { label: 'Flags', path: 'flags', count: flagsFor('CUSTOMER', this.customer.id).length },
  ];
}
