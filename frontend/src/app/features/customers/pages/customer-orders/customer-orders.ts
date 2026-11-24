import { ChangeDetectionStrategy, Component, inject } from '@angular/core';

import { PagedSection } from '../../../../shared/ui/paged-section/paged-section';
import { OrderTable } from '../../../orders/components/order-table/order-table';
import { CustomerOrdersStore } from '../../state/customer-detail.store';

/** Customer orders tab (GET /api/customers/{id}/orders); the page is kept in the URL. */
@Component({
  selector: 'app-customer-orders',
  imports: [PagedSection, OrderTable],
  providers: [CustomerOrdersStore],
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
      <app-order-table [orders]="orders.rows()" [hide]="['customer']" />
    </app-paged-section>
  `,
})
export class CustomerOrders {
  protected readonly store = inject(CustomerOrdersStore);
}
