import { ChangeDetectionStrategy, Component } from '@angular/core';

import { ListLayout } from '../../../../shared/ui/list-layout/list-layout';
import { enumOptions } from '../../../../shared/utils/enum-label';
import { TicketTable } from '../../components/ticket-table/ticket-table';
import {
  MOCK_ASSIGNEE_OPTIONS,
  MOCK_TICKET_PAGE,
  MOCK_TICKET_SELLER_OPTIONS,
} from '../../mocks/ticket-list.mock';
import { TICKET_CATEGORIES, TICKET_PRIORITIES, TICKET_STATUSES } from '../../models/ticket.model';

/** Support tickets (GET /api/tickets), most recently updated first. Mock data for now. */
@Component({
  selector: 'app-ticket-list',
  imports: [ListLayout, TicketTable],
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: { class: 'd-block h-100' },
  templateUrl: './ticket-list.html',
})
export class TicketList {
  protected readonly result = MOCK_TICKET_PAGE;
  protected readonly statusOptions = enumOptions(TICKET_STATUSES);
  protected readonly priorityOptions = enumOptions(TICKET_PRIORITIES);
  protected readonly categoryOptions = enumOptions(TICKET_CATEGORIES);
  protected readonly assignees = MOCK_ASSIGNEE_OPTIONS;
  protected readonly sellers = MOCK_TICKET_SELLER_OPTIONS;
}
