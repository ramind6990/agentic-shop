import { ChangeDetectionStrategy, Component, inject } from '@angular/core';

import { PagedSection } from '../../../../shared/ui/paged-section/paged-section';
import { TicketTable } from '../../../support/components/ticket-table/ticket-table';
import { CustomerTicketsStore } from '../../state/customer-detail.store';

/** Customer tickets tab (GET /api/customers/{id}/tickets); the page is kept in the URL. */
@Component({
  selector: 'app-customer-tickets',
  imports: [PagedSection, TicketTable],
  providers: [CustomerTicketsStore],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @let tickets = store.tickets;
    <app-paged-section
      title="Support tickets"
      [result]="tickets.value()"
      [loading]="tickets.loading()"
      [error]="tickets.error()"
      (pageChange)="tickets.setPage($event)"
      (retry)="tickets.reload()"
    >
      <app-ticket-table [tickets]="tickets.rows()" [hide]="['customer']" />
    </app-paged-section>
  `,
})
export class CustomerTickets {
  protected readonly store = inject(CustomerTicketsStore);
}
