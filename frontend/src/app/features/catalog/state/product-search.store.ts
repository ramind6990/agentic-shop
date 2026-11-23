import { Injectable, computed, inject } from '@angular/core';

import { loadOnChange, sameJson } from '../../../shared/data/load-on-change';
import { injectQueryParams, injectQueryUpdater } from '../../../shared/data/url-query';
import { CategoryApi } from '../data-access/category-api';
import { ProductApi } from '../data-access/product-api';
import {
  ProductQuery,
  attributeParamKey,
  attributeParamKeys,
  hasFilters,
  parseProductQuery,
} from '../models/product-query';
import { ProductSort } from '../models/product.model';

/**
 * State of the product search page. Provided by the ProductSearch page component, so one instance lives as long as
 * the page and is shared with its children (filter sidebar).
 *
 * Flow: actions write the URL → `query` is computed from the URL → loaders refetch → templates re-render.
 * Typing a URL, the back button and links all enter through the same `query` signal.
 */
@Injectable()
export class ProductSearchStore {
  private readonly productApi = inject(ProductApi);
  private readonly categoryApi = inject(CategoryApi);
  private readonly queryParams = injectQueryParams();
  private readonly updateUrl = injectQueryUpdater();

  /** Current search, parsed from the URL. Only changes when its content changes. */
  readonly query = computed(() => parseProductQuery(this.queryParams()), { equal: sameJson });

  /** Facets ignore sort and page, so paging does not refetch the sidebar. */
  private readonly facetQuery = computed<ProductQuery>(
    () => ({ ...this.query(), sort: 'relevance', page: 0 }),
    { equal: sameJson },
  );
  private readonly categoryId = computed(() => this.query().categoryId ?? undefined);

  private readonly results$ = loadOnChange(this.query, (q) => this.productApi.search(q));
  private readonly facets$ = loadOnChange(this.facetQuery, (q) => this.productApi.facets(q));
  private readonly category$ = loadOnChange(this.categoryId, (id) => this.categoryApi.detail(id));

  readonly results = this.results$.value;
  readonly resultsLoading = this.results$.loading;
  readonly resultsError = this.results$.error;

  readonly facets = this.facets$.value;
  readonly facetsLoading = this.facets$.loading;
  readonly facetsError = this.facets$.error;

  /** The selected category with its breadcrumbs (undefined when browsing all categories). */
  readonly category = this.category$.value;

  readonly hasFilters = computed(() => hasFilters(this.query()));

  // ---------- actions: each one only writes the URL ----------

  search(text: string): void {
    this.updateUrl({ q: text.trim(), page: null });
  }

  setSort(sort: ProductSort): void {
    this.updateUrl({ sort: sort === 'relevance' ? null : sort, page: null });
  }

  /** Attribute filters differ per category, so they are cleared when the category changes. */
  selectCategory(categoryId: number | null): void {
    this.updateUrl({
      categoryId,
      page: null,
      ...Object.fromEntries(attributeParamKeys(this.query()).map((key) => [key, null])),
    });
  }

  toggleBrand(brandId: number): void {
    const brandIds = toggle(this.query().brandIds, brandId);
    this.updateUrl({ brandIds, page: null });
  }

  setPrice(min: number | null, max: number | null): void {
    this.updateUrl({ minPrice: min, maxPrice: max, page: null });
  }

  setMinRating(rating: number | null): void {
    this.updateUrl({ minRating: rating, page: null });
  }

  setInStock(inStock: boolean): void {
    this.updateUrl({ inStock, page: null });
  }

  toggleAttributeValue(code: string, value: string): void {
    const values = toggle(this.query().attrs[code] ?? [], value);
    this.updateUrl({ [attributeParamKey(code)]: values, page: null });
  }

  setAttributeRange(code: string, min: number | null, max: number | null): void {
    this.updateUrl({
      [attributeParamKey(code, 'min')]: min,
      [attributeParamKey(code, 'max')]: max,
      page: null,
    });
  }

  setPage(page: number): void {
    this.updateUrl({ page: page > 0 ? page : null });
  }

  /** Keeps the search text, category and sort; drops every filter. */
  clearFilters(): void {
    const { q, categoryId, sort } = this.query();
    this.updateUrl({ q, categoryId, sort: sort === 'relevance' ? null : sort }, { merge: false });
  }

  reload(): void {
    this.results$.reload();
    this.facets$.reload();
  }
}

function toggle<T>(list: readonly T[], item: T): T[] {
  return list.includes(item) ? list.filter((x) => x !== item) : [...list, item];
}
