import { ChangeDetectionStrategy, Component, inject } from '@angular/core';

import { PagedSection } from '../../../../shared/ui/paged-section/paged-section';
import { OrderTable } from '../../../orders/components/order-table/order-table';
import { SellerOrdersStore } from '../../state/seller-detail.store';

/** Seller orders tab (GET /api/orders?sellerId=); the page is kept in the URL. */
@Component({
  selector: 'app-seller-orders',
  imports: [PagedSection, OrderTable],
  providers: [SellerOrdersStore],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @let orders = store.orders;
    <app-paged-section
      title="Orders"
      [result]="orders.value()"
      [loading]="orders.loading()"
      [error]="orders.error()"
      (pageChange)="orders.setPage($event)"
      (retry)="orders.reload()"
    >
      <app-order-table [orders]="orders.rows()" [hide]="['seller']" />
    </app-paged-section>
  `,
})
export class SellerOrders {
  protected readonly store = inject(SellerOrdersStore);
}
