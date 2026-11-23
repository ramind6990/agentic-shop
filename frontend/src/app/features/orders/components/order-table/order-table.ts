import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';

import { ColumnDef } from '../../../../shared/ui/data-table/column-def';
import { DataTable } from '../../../../shared/ui/data-table/data-table';
import { OrderSummary } from '../../models/order.model';

const ALL_COLUMNS: (ColumnDef<OrderSummary> & { key: string })[] = [
  { key: 'order', header: 'Order', value: (o) => o.orderNumber, link: (o) => ['/orders', o.orderNumber], mono: true },
  { key: 'placed', header: 'Placed', value: (o) => o.placedAt, type: 'datetime' },
  { key: 'customer', header: 'Customer', value: (o) => o.customer.name, link: (o) => ['/customers', o.customer.id] },
  { key: 'seller', header: 'Seller', value: (o) => o.seller.name, link: (o) => ['/sellers', o.seller.id] },
  { key: 'items', header: 'Items', value: (o) => o.itemCount, type: 'number' },
  { key: 'status', header: 'Status', value: (o) => o.status, type: 'status' },
  { key: 'total', header: 'Total', value: (o) => o.total, type: 'money', currency: (o) => o.currency },
];

/** Order rows, shared by the order list and the customer/seller order tabs. */
@Component({
  selector: 'app-order-table',
  imports: [DataTable],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <app-data-table
      [caption]="caption()"
      emptyText="No orders."
      [rows]="orders()"
      [columns]="columns()"
      [rowId]="rowId"
    />
  `,
})
export class OrderTable {
  readonly orders = input.required<OrderSummary[]>();
  readonly caption = input('Orders, newest first');
  /** Column keys to leave out, e.g. ['customer'] on a customer page. */
  readonly hide = input<string[]>([]);

  protected readonly rowId = (o: OrderSummary) => o.id;
  protected readonly columns = computed(() => ALL_COLUMNS.filter((c) => !this.hide().includes(c.key)));
}
