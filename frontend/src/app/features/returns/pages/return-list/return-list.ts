import { ChangeDetectionStrategy, Component } from '@angular/core';

import { MOCK_SELLER_REFS } from '../../../../shared/mocks/reference.mock';
import { ColumnDef } from '../../../../shared/ui/data-table/column-def';
import { DataTable } from '../../../../shared/ui/data-table/data-table';
import { ListLayout } from '../../../../shared/ui/list-layout/list-layout';
import { enumLabel, enumOptions } from '../../../../shared/utils/enum-label';
import { MOCK_RETURN_PAGE } from '../../mocks/return-list.mock';
import { RETURN_REASONS, RETURN_STATUSES, ReturnRequest } from '../../models/return.model';

/** Return requests (GET /api/returns): status, reason, seller, customer and date filters. Mock data for now. */
@Component({
  selector: 'app-return-list',
  imports: [DataTable, ListLayout],
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: { class: 'd-block h-100' },
  templateUrl: './return-list.html',
})
export class ReturnList {
  protected readonly result = MOCK_RETURN_PAGE;
  protected readonly sellers = MOCK_SELLER_REFS;
  protected readonly statusOptions = enumOptions(RETURN_STATUSES);
  protected readonly reasonOptions = enumOptions(RETURN_REASONS);

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
    { header: 'Order', value: (r) => r.order.orderNumber, link: (r) => ['/orders', r.order.orderNumber], mono: true },
    { header: 'Customer', value: (r) => r.customer.name, link: (r) => ['/customers', r.customer.id] },
    { header: 'Reason', value: (r) => enumLabel(r.reason) },
    { header: 'Status', value: (r) => r.status, type: 'status' },
    { header: 'Resolved', value: (r) => r.resolvedAt, type: 'date' },
  ];
}
