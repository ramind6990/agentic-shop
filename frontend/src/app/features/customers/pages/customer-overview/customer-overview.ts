import { DatePipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, computed, inject } from '@angular/core';
import { RouterLink } from '@angular/router';

import { LoadState } from '../../../../shared/ui/load-state/load-state';
import { SectionCard } from '../../../../shared/ui/section-card/section-card';
import { StatTile, StatTiles } from '../../../../shared/ui/stat-tiles/stat-tiles';
import { enumLabel } from '../../../../shared/utils/enum-label';
import { OrderTable } from '../../../orders/components/order-table/order-table';
import { CustomerAddressCard } from '../../components/customer-address-card/customer-address-card';
import { CustomerDetailStore, CustomerOverviewStore } from '../../state/customer-detail.store';

/** Customer overview tab: headline counts, contact details, default address and latest orders. */
@Component({
  selector: 'app-customer-overview',
  imports: [
    DatePipe,
    RouterLink,
    LoadState,
    SectionCard,
    StatTiles,
    OrderTable,
    CustomerAddressCard,
  ],
  providers: [CustomerOverviewStore],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @if (frame.customer(); as customer) {
      <app-stat-tiles class="d-block mb-4" [tiles]="tiles()" />

      <div class="row g-4">
        <div class="col-lg-4 d-flex flex-column gap-4">
          <app-section-card title="Contact">
            <dl class="kv-list">
              <dt>Email</dt>
              <dd>{{ customer.email }}</dd>
              <dt>Phone</dt>
              <dd>{{ customer.phone }}</dd>
              <dt>Status</dt>
              <dd>{{ label(customer.status) }}</dd>
              <dt>Joined</dt>
              <dd>{{ customer.createdAt | date: 'mediumDate' }}</dd>
            </dl>
          </app-section-card>
          @if (defaultAddress(); as address) {
            <app-customer-address-card [address]="address" />
          }
        </div>
        <div class="col-lg-8">
          <app-section-card title="Latest orders" [flush]="true">
            <a slot="actions" class="small" routerLink="orders">All orders</a>
            <app-load-state
              [loading]="store.loading()"
              [error]="store.error()"
              [hasValue]="!store.loading() || store.latestOrders().length > 0"
              (retry)="store.reload()"
            >
              <app-order-table [orders]="store.latestOrders()" [hide]="['customer']" />
            </app-load-state>
          </app-section-card>
        </div>
      </div>
    }
  `,
})
export class CustomerOverview {
  protected readonly frame = inject(CustomerDetailStore);
  protected readonly store = inject(CustomerOverviewStore);
  protected readonly label = enumLabel;

  protected readonly defaultAddress = computed(() =>
    this.frame.customer()?.addresses.find((a) => a.defaultAddress),
  );

  protected readonly tiles = computed<StatTile[]>(() => {
    const stats = this.frame.customer()?.stats;
    if (!stats) {
      return [];
    }
    return [
      { label: 'Orders', value: String(stats.orderCount) },
      { label: 'Returns', value: String(stats.returnCount) },
      { label: 'Tickets', value: String(stats.ticketCount) },
      {
        label: 'Active flags',
        value: String(stats.activeFlagCount),
        tone: stats.activeFlagCount > 0 ? 'danger' : undefined,
        hint: stats.activeFlagCount > 0 ? 'Fraud team review' : undefined,
      },
    ];
  });
}
