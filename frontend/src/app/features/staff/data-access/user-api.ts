import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { toHttpParams } from '../../../core/http/http-params';
import { PageResponse } from '../../../shared/models/api.model';
import { StaffUser } from '../models/staff-user.model';

export interface UserFilters {
  q?: string;
  role?: string;
  sellerId?: string | number;
}

/** HTTP calls for /api/users (staff and seller accounts). Stateless root singleton. */
@Injectable({ providedIn: 'root' })
export class UserApi {
  private readonly http = inject(HttpClient);

  list(filters: UserFilters, page: number, size = 25): Observable<PageResponse<StaffUser>> {
    const params = toHttpParams({ ...filters, page, size });
    return this.http.get<PageResponse<StaffUser>>('/api/users', { params });
  }
}
