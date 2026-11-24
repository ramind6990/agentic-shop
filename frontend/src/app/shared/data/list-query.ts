import { InjectionToken, Provider, Signal, Type, computed, inject } from '@angular/core';

import { sameJson } from './load-on-change';
import { injectQueryParams, injectQueryUpdater, intParam } from './url-query';

/** Filters of a list screen (raw strings as they are in the URL, '' when unset) plus the page index. */
export interface ListQuery<K extends string = string> {
  filters: Record<K, string>;
  /** 0-based. */
  page: number;
}

/** URL-backed state of a paged list: what is filtered, which page, and the actions that change them. */
export interface ListState<K extends string = string> {
  readonly filters: Signal<Record<K, string>>;
  /** Filters + page, compared by content: use it as the request of `loadOnChange`. */
  readonly query: Signal<ListQuery<K>>;
  readonly hasFilters: Signal<boolean>;
  setFilter(key: K, value: string): void;
  setPage(page: number): void;
  /** Removes every filter and the page from the URL. */
  clear(): void;
}

/**
 * List state kept in the URL query string: one parameter per filter key, plus `page`.
 * Typing or pasting a URL restores the filters; changing a filter writes the URL and resets the page.
 * Must be called in an injection context.
 */
export function injectListQuery<K extends string>(keys: readonly K[]): ListState<K> {
  const params = injectQueryParams();
  const updateUrl = injectQueryUpdater();

  const filters = computed(
    () => {
      const map = params();
      return Object.fromEntries(keys.map((key) => [key, map.get(key)?.trim() ?? ''])) as Record<
        K,
        string
      >;
    },
    { equal: sameJson },
  );
  const query = computed<ListQuery<K>>(
    () => ({ filters: filters(), page: intParam(params(), 'page') ?? 0 }),
    { equal: sameJson },
  );

  return {
    filters,
    query,
    hasFilters: computed(() => Object.values<string>(filters()).some((v) => v !== '')),
    setFilter: (key, value) => updateUrl({ [key]: value.trim(), page: null }),
    setPage: (page) => updateUrl({ page: page > 0 ? page : null }),
    clear: () => updateUrl({ page: null, ...Object.fromEntries(keys.map((key) => [key, null])) }),
  };
}

/** The list state the `appFilter` controls of a page bind to. */
export const LIST_STATE = new InjectionToken<ListState>('LIST_STATE');

/**
 * Exposes `store.list` to the page's `appFilter` controls. Use next to the store in the page's providers:
 *   providers: [OrderListStore, provideListState(OrderListStore)]
 */
export function provideListState(store: Type<{ readonly list: ListState<string> }>): Provider {
  return { provide: LIST_STATE, useFactory: () => inject(store).list };
}
