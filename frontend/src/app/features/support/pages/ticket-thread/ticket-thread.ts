import { ChangeDetectionStrategy, Component, input } from '@angular/core';

import { Breadcrumbs, Crumb } from '../../../../shared/ui/breadcrumbs/breadcrumbs';
import { SectionCard } from '../../../../shared/ui/section-card/section-card';
import { TicketDetails } from '../../components/ticket-details/ticket-details';
import { TicketMessageView } from '../../components/ticket-message/ticket-message';
import { MOCK_TICKET, MOCK_TICKET_MESSAGES } from '../../mocks/ticket-thread.mock';

/** Ticket thread: messages (oldest first) scroll on the left, ticket details stay on the right. */
@Component({
  selector: 'app-ticket-thread',
  imports: [Breadcrumbs, SectionCard, TicketDetails, TicketMessageView],
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: { class: 'page-fill' },
  templateUrl: './ticket-thread.html',
})
export class TicketThread {
  /** Route parameter; unused until the API is wired. */
  readonly id = input<string>();

  protected readonly ticket = MOCK_TICKET;
  protected readonly messages = MOCK_TICKET_MESSAGES;

  protected readonly crumbs: Crumb[] = [
    { label: 'Tickets', link: '/tickets' },
    { label: this.ticket.ticketNumber },
  ];
}
