import { Injectable, computed, inject } from '@angular/core';

import { loadOnChange, sameJson } from '../../../shared/data/load-on-change';
import {
  injectQueryParams,
  injectQueryUpdater,
  injectRouteParams,
  intParam,
} from '../../../shared/data/url-query';
import { ProductApi } from '../data-access/product-api';
import { parseReviewQuery } from '../models/product-query';
import { ReviewSort } from '../models/product.model';

/**
 * State of the product detail page. Provided by the ProductDetailPage component and shared with its review list.
 * The product id comes from the path (/products/:id); the review filter, sort and page come from the query string.
 */
@Injectable()
export class ProductDetailStore {
  private readonly productApi = inject(ProductApi);
  private readonly routeParams = injectRouteParams();
  private readonly queryParams = injectQueryParams();
  private readonly updateUrl = injectQueryUpdater();

  readonly productId = computed(() => intParam(this.routeParams(), 'id') ?? undefined);
  readonly reviewQuery = computed(() => parseReviewQuery(this.queryParams()), { equal: sameJson });

  private readonly reviewRequest = computed(
    () => {
      const id = this.productId();
      return id === undefined ? undefined : { id, query: this.reviewQuery() };
    },
    { equal: sameJson },
  );

  private readonly product$ = loadOnChange(this.productId, (id) => this.productApi.get(id));
  private readonly summary$ = loadOnChange(this.productId, (id) =>
    this.productApi.ratingSummary(id),
  );
  private readonly reviews$ = loadOnChange(this.reviewRequest, (r) =>
    this.productApi.reviews(r.id, r.query),
  );

  readonly product = this.product$.value;
  readonly productLoading = this.product$.loading;
  readonly productError = this.product$.error;
  readonly notFound = computed(
    () => this.productId() === undefined || this.productError()?.status === 404,
  );

  readonly ratingSummary = this.summary$.value;

  readonly reviews = this.reviews$.value;
  readonly reviewsLoading = this.reviews$.loading;
  readonly reviewsError = this.reviews$.error;

  // ---------- actions: each one only writes the URL ----------

  setReviewRating(rating: number | null): void {
    this.updateUrl({ reviewRating: rating, reviewPage: null });
  }

  setReviewSort(sort: ReviewSort): void {
    this.updateUrl({ reviewSort: sort === 'newest' ? null : sort, reviewPage: null });
  }

  setReviewPage(page: number): void {
    this.updateUrl({ reviewPage: page > 0 ? page : null });
  }

  reloadProduct(): void {
    this.product$.reload();
    this.summary$.reload();
    this.reviews$.reload();
  }

  reloadReviews(): void {
    this.reviews$.reload();
  }
}
