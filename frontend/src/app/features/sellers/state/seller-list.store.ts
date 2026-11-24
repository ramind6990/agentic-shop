import { Injectable, computed, inject } from '@angular/core';

import { MetaStore } from '../../../core/meta/meta.store';
import { injectListQuery } from '../../../shared/data/list-query';
import { loadOnChange } from '../../../shared/data/load-on-change';
import { SellerApi } from '../data-access/seller-api';

/** Seller list page: name search, status and country, kept in the URL. */
@Injectable()
export class SellerListStore {
  private readonly api = inject(SellerApi);

  readonly list = injectListQuery(['q', 'status', 'country'] as const);
  readonly statusOptions = inject(MetaStore).options('SellerStatus');

  private readonly result$ = loadOnChange(this.list.query, (q) => this.api.list(q.filters, q.page));

  readonly result = this.result$.value;
  readonly loading = this.result$.loading;
  readonly error = this.result$.error;
  readonly rows = computed(() => this.result()?.content ?? []);

  reload(): void {
    this.result$.reload();
  }
}
