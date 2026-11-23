import { ChangeDetectionStrategy, Component } from '@angular/core';

import { SectionCard } from '../../../../shared/ui/section-card/section-card';
import { TicketTable } from '../../../support/components/ticket-table/ticket-table';
import { MOCK_TICKET_ROWS } from '../../../support/mocks/ticket-list.mock';

/** Customer tickets tab (GET /api/customers/{id}/tickets). */
@Component({
  selector: 'app-customer-tickets',
  imports: [SectionCard, TicketTable],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <app-section-card title="Support tickets" [flush]="true">
      <app-ticket-table [tickets]="tickets" [hide]="['customer']" />
    </app-section-card>
  `,
})
export class CustomerTickets {
  protected readonly tickets = MOCK_TICKET_ROWS;
}
