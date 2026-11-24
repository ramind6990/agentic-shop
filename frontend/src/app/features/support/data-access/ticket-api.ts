import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { toHttpParams } from '../../../core/http/http-params';
import { PageResponse } from '../../../shared/models/api.model';
import { TicketDetail, TicketMessage, TicketSummary } from '../models/ticket.model';

export interface TicketFilters {
  q?: string;
  status?: string;
  priority?: string;
  category?: string;
  tier?: string;
  customerId?: string | number;
  assigneeId?: string | number;
  sellerId?: string | number;
}

/** HTTP calls for /api/tickets. Stateless root singleton. */
@Injectable({ providedIn: 'root' })
export class TicketApi {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = '/api/tickets';

  list(filters: TicketFilters, page: number, size = 25): Observable<PageResponse<TicketSummary>> {
    const params = toHttpParams({ ...filters, page, size });
    return this.http.get<PageResponse<TicketSummary>>(this.baseUrl, { params });
  }

  get(id: number): Observable<TicketDetail> {
    return this.http.get<TicketDetail>(`${this.baseUrl}/${id}`);
  }

  /** Oldest first. */
  messages(id: number, page: number, size = 50): Observable<PageResponse<TicketMessage>> {
    const params = toHttpParams({ page, size });
    return this.http.get<PageResponse<TicketMessage>>(`${this.baseUrl}/${id}/messages`, { params });
  }
}
