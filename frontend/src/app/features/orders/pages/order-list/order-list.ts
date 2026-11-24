import { ChangeDetectionStrategy, Component, inject } from '@angular/core';

import { provideListState } from '../../../../shared/data/list-query';
import { FilterControl } from '../../../../shared/ui/filter-control/filter-control';
import { ListLayout } from '../../../../shared/ui/list-layout/list-layout';
import { OrderTable } from '../../components/order-table/order-table';
import { OrderListStore } from '../../state/order-list.store';

/** Order list with filters (number, status, customer, seller, date range), all kept in the URL. */
@Component({
  selector: 'app-order-list',
  imports: [FilterControl, ListLayout, OrderTable],
  providers: [OrderListStore, provideListState(OrderListStore)],
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: { class: 'd-block h-100' },
  templateUrl: './order-list.html',
})
export class OrderList {
  protected readonly store = inject(OrderListStore);
}
