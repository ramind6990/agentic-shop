import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { toHttpParams } from '../../../core/http/http-params';
import { PageResponse } from '../../../shared/models/api.model';
import { Payout, SellerContract, SellerDetail, SellerSummary } from '../models/seller.model';

export interface SellerFilters {
  q?: string;
  status?: string;
  country?: string;
}

/** HTTP calls for /api/sellers. Stateless root singleton. */
@Injectable({ providedIn: 'root' })
export class SellerApi {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = '/api/sellers';

  list(filters: SellerFilters, page: number, size = 25): Observable<PageResponse<SellerSummary>> {
    const params = toHttpParams({ ...filters, page, size });
    return this.http.get<PageResponse<SellerSummary>>(this.baseUrl, { params });
  }

  get(idOrSlug: string): Observable<SellerDetail> {
    return this.http.get<SellerDetail>(`${this.baseUrl}/${encodeURIComponent(idOrSlug)}`);
  }

  /** Every fee agreement, newest first (not paged). */
  contracts(id: number): Observable<SellerContract[]> {
    return this.http.get<SellerContract[]>(`${this.baseUrl}/${id}/contracts`);
  }

  /** Newest first. */
  payouts(id: number, page: number, size = 20): Observable<PageResponse<Payout>> {
    const params = toHttpParams({ page, size });
    return this.http.get<PageResponse<Payout>>(`${this.baseUrl}/${id}/payouts`, { params });
  }
}
