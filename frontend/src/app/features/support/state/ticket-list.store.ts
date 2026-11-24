import { Injectable, computed, inject } from '@angular/core';
import { forkJoin, map } from 'rxjs';

import { MetaStore } from '../../../core/meta/meta.store';
import { injectListQuery } from '../../../shared/data/list-query';
import { loadOnChange } from '../../../shared/data/load-on-change';
import { UserApi } from '../../staff/data-access/user-api';
import { TicketApi } from '../data-access/ticket-api';
import { AuthStore } from '../../../core/auth/auth.store';

/** Ticket list page: every filter and the page live in the URL. */
@Injectable()
export class TicketListStore {
  private readonly api = inject(TicketApi);
  private readonly users = inject(UserApi);
  private readonly auth = inject(AuthStore);
  private readonly meta = inject(MetaStore);

  readonly list = injectListQuery([
    'q',
    'status',
    'priority',
    'category',
    'tier',
    'assigneeId',
    'sellerId',
    'customerId',
  ] as const);

  readonly statusOptions = this.meta.options('TicketStatus');
  readonly priorityOptions = this.meta.options('TicketPriority');
  readonly categoryOptions = this.meta.options('TicketCategory');

  /**
   * Support agents (Tier 1 and Tier 2) for the assignee dropdown, loaded once per page visit. Only for users who
   * may list staff (the dropdown is hidden for the others).
   */
  private readonly agents$ = loadOnChange(
    () => (this.auth.can('USERS_READ') ? true : undefined),
    () =>
      forkJoin([
        this.users.list({ role: 'SUPPORT_T1' }, 0, 100),
        this.users.list({ role: 'SUPPORT_T2' }, 0, 100),
      ]).pipe(
        map(([t1, t2]) =>
          [...t1.content, ...t2.content].sort((a, b) => a.fullName.localeCompare(b.fullName)),
        ),
      ),
  );
  readonly agents = computed(() => this.agents$.value() ?? []);

  private readonly result$ = loadOnChange(this.list.query, (q) => this.api.list(q.filters, q.page));

  readonly result = this.result$.value;
  readonly loading = this.result$.loading;
  readonly error = this.result$.error;
  readonly rows = computed(() => this.result()?.content ?? []);

  reload(): void {
    this.result$.reload();
  }
}
