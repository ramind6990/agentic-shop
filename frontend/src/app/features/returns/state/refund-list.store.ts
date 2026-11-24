import { Injectable, computed, inject } from '@angular/core';

import { MetaStore } from '../../../core/meta/meta.store';
import { injectListQuery } from '../../../shared/data/list-query';
import { loadOnChange } from '../../../shared/data/load-on-change';
import { ReturnApi } from '../data-access/return-api';

/** Refunds page: filters and page live in the URL. */
@Injectable()
export class RefundListStore {
  private readonly api = inject(ReturnApi);

  readonly list = injectListQuery(['status', 'orderId', 'sellerId', 'from', 'to'] as const);
  readonly statusOptions = inject(MetaStore).options('RefundStatus');

  private readonly result$ = loadOnChange(this.list.query, (q) =>
    this.api.refunds(q.filters, q.page),
  );

  readonly result = this.result$.value;
  readonly loading = this.result$.loading;
  readonly error = this.result$.error;
  readonly rows = computed(() => this.result()?.content ?? []);

  reload(): void {
    this.result$.reload();
  }
}
