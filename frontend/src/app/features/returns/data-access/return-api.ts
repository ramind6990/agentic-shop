import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { dayAfter, dayStart } from '../../../core/http/date-range';
import { toHttpParams } from '../../../core/http/http-params';
import { PageResponse } from '../../../shared/models/api.model';
import { Refund, ReturnRequest } from '../models/return.model';

/** Filters of GET /api/returns; dates are yyyy-mm-dd, empty strings mean "not set". */
export interface ReturnFilters {
  status?: string;
  reason?: string;
  sellerId?: string;
  customerId?: string;
  from?: string;
  to?: string;
}

/** Filters of GET /api/refunds. */
export interface RefundFilters {
  status?: string;
  orderId?: string;
  sellerId?: string;
  from?: string;
  to?: string;
}

/** HTTP calls for /api/returns and /api/refunds. Stateless root singleton. */
@Injectable({ providedIn: 'root' })
export class ReturnApi {
  private readonly http = inject(HttpClient);

  returns(
    filters: ReturnFilters,
    page: number,
    size = 25,
  ): Observable<PageResponse<ReturnRequest>> {
    const params = toHttpParams({
      ...filters,
      from: filters.from ? dayStart(filters.from) : null,
      to: filters.to ? dayAfter(filters.to) : null,
      page,
      size,
    });
    return this.http.get<PageResponse<ReturnRequest>>('/api/returns', { params });
  }

  getReturn(id: number): Observable<ReturnRequest> {
    return this.http.get<ReturnRequest>(`/api/returns/${id}`);
  }

  refunds(filters: RefundFilters, page: number, size = 25): Observable<PageResponse<Refund>> {
    const params = toHttpParams({
      ...filters,
      from: filters.from ? dayStart(filters.from) : null,
      to: filters.to ? dayAfter(filters.to) : null,
      page,
      size,
    });
    return this.http.get<PageResponse<Refund>>('/api/refunds', { params });
  }
}
