import { ChangeDetectionStrategy, Component, inject } from '@angular/core';

import { provideListState } from '../../../../shared/data/list-query';
import { ColumnDef } from '../../../../shared/ui/data-table/column-def';
import { DataTable } from '../../../../shared/ui/data-table/data-table';
import { FilterControl } from '../../../../shared/ui/filter-control/filter-control';
import { ListLayout } from '../../../../shared/ui/list-layout/list-layout';
import { enumLabel } from '../../../../shared/utils/enum-label';
import { ReturnRequest } from '../../models/return.model';
import { ReturnListStore } from '../../state/return-list.store';
import { AuthStore } from '../../../../core/auth/auth.store';

/** Return requests (GET /api/returns): status, reason, seller, customer and date filters. */
@Component({
  selector: 'app-return-list',
  imports: [DataTable, FilterControl, ListLayout],
  providers: [ReturnListStore, provideListState(ReturnListStore)],
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: { class: 'd-block h-100' },
  templateUrl: './return-list.html',
})
export class ReturnList {
  protected readonly auth = inject(AuthStore);
  protected readonly store = inject(ReturnListStore);

  protected readonly rowId = (r: ReturnRequest) => r.id;
  protected readonly columns: ColumnDef<ReturnRequest>[] = [
    { header: 'Return', value: (r) => `#${r.id}`, link: (r) => ['/returns', r.id] },
    { header: 'Opened', value: (r) => r.openedAt, type: 'datetime' },
    {
      header: 'Product',
      value: (r) => r.product.name,
      link: (r) => ['/products', r.product.id],
      hint: (r) => `Qty ${r.quantity}`,
    },
    {
      header: 'Order',
      value: (r) => r.order.orderNumber,
      link: (r) => ['/orders', r.order.orderNumber],
      mono: true,
    },
    {
      header: 'Customer',
      value: (r) => r.customer.name,
      link: (r) => ['/customers', r.customer.id],
    },
    { header: 'Reason', value: (r) => enumLabel(r.reason) },
    { header: 'Status', value: (r) => r.status, type: 'status' },
    { header: 'Resolved', value: (r) => r.resolvedAt, type: 'date' },
  ];
}
