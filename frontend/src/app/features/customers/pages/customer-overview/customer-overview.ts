import { DatePipe } from '@angular/common';
import { ChangeDetectionStrategy, Component } from '@angular/core';
import { RouterLink } from '@angular/router';

import { SectionCard } from '../../../../shared/ui/section-card/section-card';
import { StatTile, StatTiles } from '../../../../shared/ui/stat-tiles/stat-tiles';
import { OrderTable } from '../../../orders/components/order-table/order-table';
import { MOCK_ORDER_PAGE } from '../../../orders/mocks/order-list.mock';
import { CustomerAddressCard } from '../../components/customer-address-card/customer-address-card';
import { MOCK_CUSTOMER } from '../../mocks/customer-detail.mock';

/** Customer overview tab: headline counts, contact details, default address and latest orders. */
@Component({
  selector: 'app-customer-overview',
  imports: [DatePipe, RouterLink, SectionCard, StatTiles, OrderTable, CustomerAddressCard],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <app-stat-tiles class="d-block mb-4" [tiles]="tiles" />

    <div class="row g-4">
      <div class="col-lg-4 d-flex flex-column gap-4">
        <app-section-card title="Contact">
          <dl class="kv-list">
            <dt>Email</dt>
            <dd>{{ customer.email }}</dd>
            <dt>Phone</dt>
            <dd>{{ customer.phone }}</dd>
            <dt>Status</dt>
            <dd>{{ customer.status === 'ACTIVE' ? 'Active' : 'Suspended' }}</dd>
            <dt>Joined</dt>
            <dd>{{ customer.createdAt | date: 'mediumDate' }}</dd>
          </dl>
        </app-section-card>
        @if (defaultAddress; as address) {
          <app-customer-address-card [address]="address" />
        }
      </div>
      <div class="col-lg-8">
        <app-section-card title="Latest orders" [flush]="true">
          <a slot="actions" class="small" routerLink="orders">All orders</a>
          <app-order-table [orders]="latestOrders" [hide]="['customer']" />
        </app-section-card>
      </div>
    </div>
  `,
})
export class CustomerOverview {
  protected readonly customer = MOCK_CUSTOMER;
  protected readonly defaultAddress = this.customer.addresses.find((a) => a.defaultAddress);
  protected readonly latestOrders = MOCK_ORDER_PAGE.content
    .filter((o) => o.customer.id === this.customer.id)
    .slice(0, 5);

  protected readonly tiles: StatTile[] = [
    { label: 'Orders', value: String(this.customer.stats.orderCount) },
    { label: 'Returns', value: String(this.customer.stats.returnCount) },
    { label: 'Tickets', value: String(this.customer.stats.ticketCount) },
    {
      label: 'Active flags',
      value: String(this.customer.stats.activeFlagCount),
      tone: this.customer.stats.activeFlagCount > 0 ? 'danger' : undefined,
      hint: this.customer.stats.activeFlagCount > 0 ? 'Fraud team review' : undefined,
    },
  ];
}
