import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { toHttpParams } from '../../../core/http/http-params';
import { PageResponse } from '../../../shared/models/api.model';
import { OrderSummary } from '../../orders/models/order.model';
import { TicketSummary } from '../../support/models/ticket.model';
import { CustomerDetail, CustomerReview, CustomerSummary } from '../models/customer.model';

export interface CustomerFilters {
  q?: string;
  status?: string;
}

/** HTTP calls for /api/customers. Stateless root singleton. */
@Injectable({ providedIn: 'root' })
export class CustomerApi {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = '/api/customers';

  list(
    filters: CustomerFilters,
    page: number,
    size = 25,
  ): Observable<PageResponse<CustomerSummary>> {
    const params = toHttpParams({ ...filters, page, size });
    return this.http.get<PageResponse<CustomerSummary>>(this.baseUrl, { params });
  }

  get(id: number): Observable<CustomerDetail> {
    return this.http.get<CustomerDetail>(`${this.baseUrl}/${id}`);
  }

  orders(id: number, page: number, size = 20): Observable<PageResponse<OrderSummary>> {
    const params = toHttpParams({ page, size });
    return this.http.get<PageResponse<OrderSummary>>(`${this.baseUrl}/${id}/orders`, { params });
  }

  tickets(id: number, page: number, size = 20): Observable<PageResponse<TicketSummary>> {
    const params = toHttpParams({ page, size });
    return this.http.get<PageResponse<TicketSummary>>(`${this.baseUrl}/${id}/tickets`, { params });
  }

  reviews(id: number, page: number, size = 20): Observable<PageResponse<CustomerReview>> {
    const params = toHttpParams({ page, size });
    return this.http.get<PageResponse<CustomerReview>>(`${this.baseUrl}/${id}/reviews`, { params });
  }
}
