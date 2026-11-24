import { Injectable, computed, inject } from '@angular/core';

import { MetaStore } from '../../../core/meta/meta.store';
import { injectListQuery } from '../../../shared/data/list-query';
import { loadOnChange } from '../../../shared/data/load-on-change';
import { OrderApi } from '../data-access/order-api';

/** Order list page: filters and page live in the URL; the result reloads whenever they change. */
@Injectable()
export class OrderListStore {
  private readonly api = inject(OrderApi);

  readonly list = injectListQuery(['q', 'status', 'customerId', 'sellerId', 'from', 'to'] as const);
  readonly statusOptions = inject(MetaStore).options('OrderStatus');

  private readonly result$ = loadOnChange(this.list.query, (q) => this.api.list(q.filters, q.page));

  readonly result = this.result$.value;
  readonly loading = this.result$.loading;
  readonly error = this.result$.error;
  readonly rows = computed(() => this.result()?.content ?? []);

  reload(): void {
    this.result$.reload();
  }
}
