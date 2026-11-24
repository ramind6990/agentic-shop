import { Injectable, computed, inject } from '@angular/core';

import { MetaStore } from '../../../core/meta/meta.store';
import { injectListQuery } from '../../../shared/data/list-query';
import { loadOnChange } from '../../../shared/data/load-on-change';
import { UserApi } from '../data-access/user-api';

/** Staff users page: name/email search, role and seller, kept in the URL. */
@Injectable()
export class StaffListStore {
  private readonly api = inject(UserApi);

  readonly list = injectListQuery(['q', 'role', 'sellerId'] as const);
  /** Every role except CUSTOMER: customers have their own screen. */
  readonly roleOptions = inject(MetaStore).options('Role', ['CUSTOMER']);

  private readonly result$ = loadOnChange(this.list.query, (q) => this.api.list(q.filters, q.page));

  readonly result = this.result$.value;
  readonly loading = this.result$.loading;
  readonly error = this.result$.error;
  readonly rows = computed(() => this.result()?.content ?? []);

  reload(): void {
    this.result$.reload();
  }
}
