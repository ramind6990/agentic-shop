import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { CategoryDetail } from '../models/category.model';

/** HTTP calls for /api/categories. Stateless root singleton; tree, filters and brands follow with their screens. */
@Injectable({ providedIn: 'root' })
export class CategoryApi {
  private readonly http = inject(HttpClient);

  detail(idOrSlug: number | string): Observable<CategoryDetail> {
    return this.http.get<CategoryDetail>(`/api/categories/${idOrSlug}`);
  }
}
