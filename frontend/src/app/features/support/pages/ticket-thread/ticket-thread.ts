import { ChangeDetectionStrategy, Component, computed, inject } from '@angular/core';

import { Breadcrumbs, Crumb } from '../../../../shared/ui/breadcrumbs/breadcrumbs';
import { LoadState } from '../../../../shared/ui/load-state/load-state';
import { NotFoundPanel } from '../../../../shared/ui/not-found-panel/not-found-panel';
import { SectionCard } from '../../../../shared/ui/section-card/section-card';
import { TicketDetails } from '../../components/ticket-details/ticket-details';
import { TicketMessageView } from '../../components/ticket-message/ticket-message';
import { TicketThreadStore } from '../../state/ticket-thread.store';

/** Ticket thread: messages (oldest first) scroll on the left, ticket details stay on the right. */
@Component({
  selector: 'app-ticket-thread',
  imports: [Breadcrumbs, LoadState, NotFoundPanel, SectionCard, TicketDetails, TicketMessageView],
  providers: [TicketThreadStore],
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: { class: 'page-fill' },
  templateUrl: './ticket-thread.html',
})
export class TicketThread {
  protected readonly store = inject(TicketThreadStore);

  protected readonly crumbs = computed<Crumb[]>(() => [
    { label: 'Tickets', link: '/tickets' },
    { label: this.store.ticket()?.ticketNumber ?? '' },
  ]);
}
