import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { toHttpParams } from '../../../core/http/http-params';
import { PageResponse } from '../../../shared/models/api.model';
import {
  PRODUCT_PAGE_SIZE,
  ProductQuery,
  REVIEW_PAGE_SIZE,
  ReviewQuery,
  productFilterParams,
} from '../models/product-query';
import {
  ProductDetail,
  ProductFacets,
  ProductSummary,
  RatingSummary,
  Review,
} from '../models/product.model';

/**
 * HTTP calls for /api/products. Stateless: it only maps queries to requests and returns Observables.
 * A root singleton so other features (e.g. a seller's products tab) can reuse it.
 */
@Injectable({ providedIn: 'root' })
export class ProductApi {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = '/api/products';

  search(query: ProductQuery, size = PRODUCT_PAGE_SIZE): Observable<PageResponse<ProductSummary>> {
    const params = toHttpParams({
      ...productFilterParams(query),
      sort: query.sort,
      page: query.page,
      size,
    });
    return this.http.get<PageResponse<ProductSummary>>(this.baseUrl, { params });
  }

  facets(query: ProductQuery): Observable<ProductFacets> {
    const params = toHttpParams(productFilterParams(query));
    return this.http.get<ProductFacets>(`${this.baseUrl}/facets`, { params });
  }

  get(id: number): Observable<ProductDetail> {
    return this.http.get<ProductDetail>(`${this.baseUrl}/${id}`);
  }

  reviews(
    id: number,
    query: ReviewQuery,
    size = REVIEW_PAGE_SIZE,
  ): Observable<PageResponse<Review>> {
    const params = toHttpParams({
      rating: query.rating,
      sort: query.sort,
      page: query.page,
      size,
    });
    return this.http.get<PageResponse<Review>>(`${this.baseUrl}/${id}/reviews`, { params });
  }

  ratingSummary(id: number): Observable<RatingSummary> {
    return this.http.get<RatingSummary>(`${this.baseUrl}/${id}/reviews/summary`);
  }
}
