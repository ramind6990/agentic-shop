import { ChangeDetectionStrategy, Component, inject } from '@angular/core';

import { provideListState } from '../../../../shared/data/list-query';
import { FilterControl } from '../../../../shared/ui/filter-control/filter-control';
import { ListLayout } from '../../../../shared/ui/list-layout/list-layout';
import { TicketTable } from '../../components/ticket-table/ticket-table';
import { TicketListStore } from '../../state/ticket-list.store';

/** Support tickets (GET /api/tickets), most recently updated first. Filters and page live in the URL. */
@Component({
  selector: 'app-ticket-list',
  imports: [FilterControl, ListLayout, TicketTable],
  providers: [TicketListStore, provideListState(TicketListStore)],
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: { class: 'd-block h-100' },
  templateUrl: './ticket-list.html',
})
export class TicketList {
  protected readonly store = inject(TicketListStore);
}
