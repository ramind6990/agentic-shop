import { Injectable, computed, inject } from '@angular/core';

import { MetaStore } from '../../../core/meta/meta.store';
import { injectListQuery } from '../../../shared/data/list-query';
import { loadOnChange } from '../../../shared/data/load-on-change';
import { ReturnApi } from '../data-access/return-api';

/** Return requests page: filters and page live in the URL. */
@Injectable()
export class ReturnListStore {
  private readonly api = inject(ReturnApi);
  private readonly meta = inject(MetaStore);

  readonly list = injectListQuery([
    'status',
    'reason',
    'sellerId',
    'customerId',
    'from',
    'to',
  ] as const);
  readonly statusOptions = this.meta.options('ReturnStatus');
  readonly reasonOptions = this.meta.options('ReturnReason');

  private readonly result$ = loadOnChange(this.list.query, (q) =>
    this.api.returns(q.filters, q.page),
  );

  readonly result = this.result$.value;
  readonly loading = this.result$.loading;
  readonly error = this.result$.error;
  readonly rows = computed(() => this.result()?.content ?? []);

  reload(): void {
    this.result$.reload();
  }
}
