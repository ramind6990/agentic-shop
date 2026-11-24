import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { toHttpParams } from '../../../core/http/http-params';
import { PageResponse, Ref } from '../../../shared/models/api.model';
import { CategoryDetail, CategoryNode } from '../models/category.model';

export interface BrandQuery {
  q: string;
  categoryId: string;
  page: number;
}

/** HTTP calls for /api/categories and /api/brands. Stateless root singleton. */
@Injectable({ providedIn: 'root' })
export class CategoryApi {
  private readonly http = inject(HttpClient);

  tree(): Observable<CategoryNode[]> {
    return this.http.get<CategoryNode[]>('/api/categories');
  }

  detail(idOrSlug: number | string): Observable<CategoryDetail> {
    return this.http.get<CategoryDetail>(`/api/categories/${idOrSlug}`);
  }

  brands(query: BrandQuery, size = 50): Observable<PageResponse<Ref>> {
    const params = toHttpParams({ ...query, size });
    return this.http.get<PageResponse<Ref>>('/api/brands', { params });
  }
}
