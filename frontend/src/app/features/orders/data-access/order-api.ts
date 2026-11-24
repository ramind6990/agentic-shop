import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { dayAfter, dayStart } from '../../../core/http/date-range';
import { toHttpParams } from '../../../core/http/http-params';
import { PageResponse } from '../../../shared/models/api.model';
import { OrderDetail, OrderSummary } from '../models/order.model';

/** Filters of GET /api/orders; dates are yyyy-mm-dd, empty strings mean "not set". */
export interface OrderFilters {
  q?: string;
  status?: string;
  customerId?: string | number;
  sellerId?: string | number;
  from?: string;
  to?: string;
}

/** HTTP calls for /api/orders. Stateless root singleton, also used by the customer and seller order tabs. */
@Injectable({ providedIn: 'root' })
export class OrderApi {
  private readonly http = inject(HttpClient);

  list(filters: OrderFilters, page: number, size = 25): Observable<PageResponse<OrderSummary>> {
    const params = toHttpParams({
      q: filters.q,
      status: filters.status,
      customerId: filters.customerId,
      sellerId: filters.sellerId,
      from: filters.from ? dayStart(filters.from) : null,
      to: filters.to ? dayAfter(filters.to) : null,
      page,
      size,
    });
    return this.http.get<PageResponse<OrderSummary>>('/api/orders', { params });
  }

  /** By id or order number (AS-250314-004521). */
  get(idOrNumber: string): Observable<OrderDetail> {
    return this.http.get<OrderDetail>(`/api/orders/${encodeURIComponent(idOrNumber)}`);
  }
}
