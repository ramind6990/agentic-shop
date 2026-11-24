import { Signal, computed } from '@angular/core';
import { Observable } from 'rxjs';

import { PageResponse } from '../models/api.model';
import { injectListQuery } from './list-query';
import { Loaded, loadOnChange, sameJson } from './load-on-change';

export interface PagedLoaded<T> extends Loaded<PageResponse<T>> {
  /** Rows of the current page, [] until loaded. */
  readonly rows: Signal<T[]>;
  setPage(page: number): void;
}

/**
 * A paged list that belongs to something else (a customer's orders, a seller's payouts…): reloads when the owner
 * (`request`) or the `page` URL parameter changes. Used by detail-page tabs. Call in an injection context.
 */
export function pagedLoader<R, T>(
  request: () => R | undefined,
  load: (request: R, page: number) => Observable<PageResponse<T>>,
): PagedLoaded<T> {
  const list = injectListQuery([]);
  const pagedRequest = computed(
    () => {
      const owner = request();
      return owner === undefined ? undefined : { owner, page: list.query().page };
    },
    { equal: sameJson },
  );
  const loaded = loadOnChange(pagedRequest, ({ owner, page }) => load(owner, page));
  return {
    ...loaded,
    rows: computed(() => loaded.value()?.content ?? []),
    setPage: list.setPage,
  };
}
