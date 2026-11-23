import { ChangeDetectionStrategy, Component } from '@angular/core';

import { ListLayout } from '../../../../shared/ui/list-layout/list-layout';
import { enumLabel } from '../../../../shared/utils/enum-label';
import { OrderTable } from '../../components/order-table/order-table';
import { MOCK_ORDER_PAGE, MOCK_SELLER_OPTIONS } from '../../mocks/order-list.mock';
import { ORDER_STATUSES } from '../../models/order.model';

/** Order list with filters (number, status, customer, seller, date range). Mock data for now. */
@Component({
  selector: 'app-order-list',
  imports: [ListLayout, OrderTable],
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: { class: 'd-block h-100' },
  templateUrl: './order-list.html',
})
export class OrderList {
  protected readonly result = MOCK_ORDER_PAGE;
  protected readonly sellers = MOCK_SELLER_OPTIONS;
  protected readonly statusOptions = ORDER_STATUSES.map((value) => ({ value, label: enumLabel(value) }));
}
