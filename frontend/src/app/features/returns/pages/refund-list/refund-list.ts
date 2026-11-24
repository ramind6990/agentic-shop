import { ChangeDetectionStrategy, Component, inject } from '@angular/core';

import { provideListState } from '../../../../shared/data/list-query';
import { ColumnDef } from '../../../../shared/ui/data-table/column-def';
import { DataTable } from '../../../../shared/ui/data-table/data-table';
import { FilterControl } from '../../../../shared/ui/filter-control/filter-control';
import { ListLayout } from '../../../../shared/ui/list-layout/list-layout';
import { Refund } from '../../models/return.model';
import { RefundListStore } from '../../state/refund-list.store';
import { AuthStore } from '../../../../core/auth/auth.store';

/** Refunds (GET /api/refunds): from returns, lost parcels and late-delivery goodwill credits. */
@Component({
  selector: 'app-refund-list',
  imports: [DataTable, FilterControl, ListLayout],
  providers: [RefundListStore, provideListState(RefundListStore)],
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: { class: 'd-block h-100' },
  templateUrl: './refund-list.html',
})
export class RefundList {
  protected readonly auth = inject(AuthStore);
  protected readonly store = inject(RefundListStore);

  protected readonly rowId = (r: Refund) => r.id;
  protected readonly columns: ColumnDef<Refund>[] = [
    { header: 'Created', value: (r) => r.createdAt, type: 'datetime' },
    {
      header: 'Order',
      value: (r) => r.order.orderNumber,
      link: (r) => ['/orders', r.order.orderNumber],
      mono: true,
    },
    {
      header: 'Reason',
      value: (r) => r.reason,
      hint: (r) => (r.returnRequestId ? `From return #${r.returnRequestId}` : null),
    },
    {
      header: 'Issued by',
      value: (r) => r.issuedBy?.name ?? null,
      hint: (r) => (r.amount > 100 ? 'Tier 2 (above $100)' : 'Tier 1'),
    },
    { header: 'Status', value: (r) => r.status, type: 'status' },
    { header: 'Amount', value: (r) => r.amount, type: 'money' },
  ];
}
