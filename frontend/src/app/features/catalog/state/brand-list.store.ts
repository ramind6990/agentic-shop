import { Injectable, computed, inject } from '@angular/core';

import { injectListQuery } from '../../../shared/data/list-query';
import { loadOnChange } from '../../../shared/data/load-on-change';
import { CategoryApi } from '../data-access/category-api';

/** Brand list page: name search and "has products in category", kept in the URL. */
@Injectable()
export class BrandListStore {
  private readonly api = inject(CategoryApi);

  readonly list = injectListQuery(['q', 'categoryId'] as const);
  private readonly result$ = loadOnChange(this.list.query, (q) =>
    this.api.brands({ ...q.filters, page: q.page }),
  );

  readonly result = this.result$.value;
  readonly loading = this.result$.loading;
  readonly error = this.result$.error;
  readonly rows = computed(() => this.result()?.content ?? []);

  reload(): void {
    this.result$.reload();
  }
}
