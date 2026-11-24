import { Injectable, computed, inject } from '@angular/core';

import { MetaStore } from '../../../core/meta/meta.store';
import { injectListQuery } from '../../../shared/data/list-query';
import { loadOnChange } from '../../../shared/data/load-on-change';
import { CustomerApi } from '../data-access/customer-api';

/** Customer list page: name/email search and status, kept in the URL. */
@Injectable()
export class CustomerListStore {
  private readonly api = inject(CustomerApi);

  readonly list = injectListQuery(['q', 'status'] as const);
  readonly statusOptions = inject(MetaStore).options('UserStatus');

  private readonly result$ = loadOnChange(this.list.query, (q) => this.api.list(q.filters, q.page));

  readonly result = this.result$.value;
  readonly loading = this.result$.loading;
  readonly error = this.result$.error;
  readonly rows = computed(() => this.result()?.content ?? []);

  reload(): void {
    this.result$.reload();
  }
}
