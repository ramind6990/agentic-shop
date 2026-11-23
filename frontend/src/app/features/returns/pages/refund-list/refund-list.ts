import { ChangeDetectionStrategy, Component } from '@angular/core';

import { MOCK_SELLER_REFS } from '../../../../shared/mocks/reference.mock';
import { ColumnDef } from '../../../../shared/ui/data-table/column-def';
import { DataTable } from '../../../../shared/ui/data-table/data-table';
import { ListLayout } from '../../../../shared/ui/list-layout/list-layout';
import { enumOptions } from '../../../../shared/utils/enum-label';
import { MOCK_REFUND_PAGE } from '../../mocks/return-list.mock';
import { REFUND_STATUSES, Refund } from '../../models/return.model';

/** Refunds (GET /api/refunds): from returns, lost parcels and late-delivery goodwill credits. Mock data for now. */
@Component({
  selector: 'app-refund-list',
  imports: [DataTable, ListLayout],
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: { class: 'd-block h-100' },
  templateUrl: './refund-list.html',
})
export class RefundList {
  protected readonly result = MOCK_REFUND_PAGE;
  protected readonly sellers = MOCK_SELLER_REFS;
  protected readonly statusOptions = enumOptions(REFUND_STATUSES);

  protected readonly rowId = (r: Refund) => r.id;
  protected readonly columns: ColumnDef<Refund>[] = [
    { header: 'Created', value: (r) => r.createdAt, type: 'datetime' },
    { header: 'Order', value: (r) => r.order.orderNumber, link: (r) => ['/orders', r.order.orderNumber], mono: true },
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
