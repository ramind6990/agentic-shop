import { DatePipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, input } from '@angular/core';
import { RouterLink } from '@angular/router';

import { AddressBlock } from '../../../../shared/ui/address-block/address-block';
import { Breadcrumbs, Crumb } from '../../../../shared/ui/breadcrumbs/breadcrumbs';
import { ColumnDef } from '../../../../shared/ui/data-table/column-def';
import { DataTable } from '../../../../shared/ui/data-table/data-table';
import { SectionCard } from '../../../../shared/ui/section-card/section-card';
import { StatusBadge } from '../../../../shared/ui/status-badge/status-badge';
import { ReturnCard } from '../../../returns/components/return-card/return-card';
import { Refund } from '../../../returns/models/return.model';
import { OrderTotals } from '../../components/order-totals/order-totals';
import { PaymentList } from '../../components/payment-list/payment-list';
import { ShipmentList } from '../../components/shipment-list/shipment-list';
import { MOCK_ORDER_DETAIL } from '../../mocks/order-detail.mock';
import { OrderItem } from '../../models/order.model';

/** Order detail: lines, returns and refunds on the left; totals, address, payments, shipments on the right. */
@Component({
  selector: 'app-order-detail',
  imports: [
    DatePipe,
    RouterLink,
    AddressBlock,
    Breadcrumbs,
    DataTable,
    SectionCard,
    StatusBadge,
    ReturnCard,
    OrderTotals,
    PaymentList,
    ShipmentList,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './order-detail.html',
})
export class OrderDetailPage {
  /** Route parameter (id or order number); unused until the API is wired. */
  readonly idOrNumber = input<string>();

  protected readonly order = MOCK_ORDER_DETAIL;

  protected readonly crumbs: Crumb[] = [
    { label: 'Orders', link: '/orders' },
    { label: this.order.orderNumber },
  ];

  protected readonly itemId = (item: OrderItem) => item.id;
  protected readonly itemColumns: ColumnDef<OrderItem>[] = [
    {
      header: 'Product',
      value: (i) => i.productTitle,
      link: (i) => ['/products', i.productId],
      hint: (i) => i.sku,
    },
    { header: 'Unit price', value: (i) => i.unitPrice, type: 'money', currency: () => this.order.currency },
    { header: 'Qty', value: (i) => i.quantity, type: 'number' },
    { header: 'Line total', value: (i) => i.lineTotal, type: 'money', currency: () => this.order.currency },
  ];

  protected readonly refundId = (refund: Refund) => refund.id;
  protected readonly refundColumns: ColumnDef<Refund>[] = [
    { header: 'Created', value: (r) => r.createdAt, type: 'datetime' },
    {
      header: 'Reason',
      value: (r) => r.reason,
      hint: (r) => (r.returnRequestId ? `From return #${r.returnRequestId}` : null),
    },
    { header: 'Issued by', value: (r) => r.issuedBy?.name ?? null },
    { header: 'Status', value: (r) => r.status, type: 'status' },
    { header: 'Amount', value: (r) => r.amount, type: 'money', currency: () => this.order.currency },
  ];
}
