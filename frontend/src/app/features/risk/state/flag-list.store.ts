import { Injectable, computed, inject } from '@angular/core';

import { MetaStore } from '../../../core/meta/meta.store';
import { injectListQuery } from '../../../shared/data/list-query';
import { loadOnChange } from '../../../shared/data/load-on-change';
import { FlagApi } from '../data-access/flag-api';

/** Account flags page: subject, flag type and state, kept in the URL. */
@Injectable()
export class FlagListStore {
  private readonly api = inject(FlagApi);

  readonly list = injectListQuery(['subjectType', 'subjectId', 'flagType', 'active'] as const);
  readonly typeOptions = inject(MetaStore).options('FlagType');

  private readonly result$ = loadOnChange(this.list.query, (q) => this.api.list(q.filters, q.page));

  readonly result = this.result$.value;
  readonly loading = this.result$.loading;
  readonly error = this.result$.error;
  readonly rows = computed(() => this.result()?.content ?? []);

  reload(): void {
    this.result$.reload();
  }
}
