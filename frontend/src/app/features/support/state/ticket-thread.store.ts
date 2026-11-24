import { Injectable, computed, inject, linkedSignal } from '@angular/core';

import { PageResponse } from '../../../shared/models/api.model';
import { loadOnChange, sameJson } from '../../../shared/data/load-on-change';
import { injectRouteParams, intParam } from '../../../shared/data/url-query';
import { TicketApi } from '../data-access/ticket-api';
import { TicketMessage } from '../models/ticket.model';

/**
 * Ticket thread page (/tickets/:id): the ticket, and its messages oldest first. Messages are read 50 at a time and
 * appended with "Load newer messages"; how far the thread is loaded is screen state, so it is not in the URL.
 */
@Injectable()
export class TicketThreadStore {
  private readonly api = inject(TicketApi);
  private readonly params = injectRouteParams();

  readonly ticketId = computed(() => intParam(this.params(), 'id') ?? undefined);
  private readonly ticket$ = loadOnChange(this.ticketId, (id) => this.api.get(id));

  readonly ticket = this.ticket$.value;
  readonly loading = this.ticket$.loading;
  readonly error = this.ticket$.error;
  readonly notFound = computed(() => this.ticketId() === undefined || this.error()?.status === 404);

  /** Pages of messages loaded so far, minus one; back to 0 whenever the ticket changes. */
  private readonly lastPage = linkedSignal(() => {
    this.ticketId();
    return 0;
  });
  private readonly messageRequest = computed(
    () => {
      const id = this.ticketId();
      return id === undefined ? undefined : { id, page: this.lastPage() };
    },
    { equal: sameJson },
  );
  private readonly messagePage$ = loadOnChange(this.messageRequest, (r) =>
    this.api.messages(r.id, r.page),
  );

  /** Every message loaded so far: page 0 replaces the list, later pages are appended. */
  private readonly allMessages = linkedSignal<
    PageResponse<TicketMessage> | undefined,
    TicketMessage[]
  >({
    source: this.messagePage$.value,
    computation: (page, previous) => {
      if (!page) {
        return [];
      }
      return page.page === 0 ? page.content : [...(previous?.value ?? []), ...page.content];
    },
  });
  readonly messages = this.allMessages.asReadonly();
  readonly messagesLoading = this.messagePage$.loading;
  readonly messagesError = this.messagePage$.error;
  readonly hasMore = computed(() => {
    const page = this.messagePage$.value();
    return !!page && page.page < page.totalPages - 1;
  });
  readonly totalMessages = computed(() => this.messagePage$.value()?.totalElements ?? 0);

  loadMore(): void {
    if (this.hasMore() && !this.messagesLoading()) {
      this.lastPage.update((p) => p + 1);
    }
  }

  /** Reloads the ticket and the thread from its first page. */
  reload(): void {
    this.ticket$.reload();
    this.lastPage.set(0);
    this.messagePage$.reload();
  }

  /** Retries the message page that failed. */
  retryMessages(): void {
    this.messagePage$.reload();
  }
}
