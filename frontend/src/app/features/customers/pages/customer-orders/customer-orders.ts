import { ChangeDetectionStrategy, Component } from '@angular/core';

import { Pager } from '../../../../shared/ui/pager/pager';
import { SectionCard } from '../../../../shared/ui/section-card/section-card';
import { OrderTable } from '../../../orders/components/order-table/order-table';
import { MOCK_ORDER_PAGE } from '../../../orders/mocks/order-list.mock';
import { MOCK_CUSTOMER } from '../../mocks/customer-detail.mock';

/** Customer orders tab (GET /api/customers/{id}/orders). */
@Component({
  selector: 'app-customer-orders',
  imports: [Pager, SectionCard, OrderTable],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <app-section-card title="Orders" [flush]="true">
      <app-order-table [orders]="orders" [hide]="['customer']" />
      <app-pager
        class="d-block border-top px-3 py-2"
        [page]="0"
        [size]="20"
        [totalElements]="total"
        [totalPages]="2"
      />
    </app-section-card>
  `,
})
export class CustomerOrders {
  protected readonly orders = MOCK_ORDER_PAGE.content.filter((o) => o.customer.id === MOCK_CUSTOMER.id);
  protected readonly total = MOCK_CUSTOMER.stats.orderCount;
}
