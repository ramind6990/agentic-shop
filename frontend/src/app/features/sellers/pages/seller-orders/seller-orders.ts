import { ChangeDetectionStrategy, Component } from '@angular/core';

import { Pager } from '../../../../shared/ui/pager/pager';
import { SectionCard } from '../../../../shared/ui/section-card/section-card';
import { OrderTable } from '../../../orders/components/order-table/order-table';
import { MOCK_ORDER_PAGE } from '../../../orders/mocks/order-list.mock';
import { MOCK_SELLER } from '../../mocks/seller-detail.mock';

/** Seller orders tab (GET /api/orders?sellerId=). */
@Component({
  selector: 'app-seller-orders',
  imports: [Pager, SectionCard, OrderTable],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <app-section-card title="Orders" [flush]="true">
      <app-order-table [orders]="orders" [hide]="['seller']" />
      <app-pager
        class="d-block border-top px-3 py-2"
        [page]="0"
        [size]="25"
        [totalElements]="total"
        [totalPages]="totalPages"
      />
    </app-section-card>
  `,
})
export class SellerOrders {
  protected readonly orders = MOCK_ORDER_PAGE.content.filter((o) => o.seller.id === MOCK_SELLER.id);
  protected readonly total = MOCK_SELLER.stats.orderCount;
  protected readonly totalPages = Math.ceil(this.total / 25);
}
