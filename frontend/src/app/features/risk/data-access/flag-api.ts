import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { toHttpParams } from '../../../core/http/http-params';
import { PageResponse } from '../../../shared/models/api.model';
import { AccountFlag } from '../models/flag.model';

export interface FlagFilters {
  subjectType?: string;
  subjectId?: string | number;
  flagType?: string;
  /** 'true' / 'false' / '' (both). */
  active?: string;
}

/** HTTP calls for /api/flags (fraud-team data). Stateless root singleton, also used by customer/seller tabs. */
@Injectable({ providedIn: 'root' })
export class FlagApi {
  private readonly http = inject(HttpClient);

  list(filters: FlagFilters, page: number, size = 25): Observable<PageResponse<AccountFlag>> {
    const params = toHttpParams({ ...filters, page, size });
    return this.http.get<PageResponse<AccountFlag>>('/api/flags', { params });
  }
}
