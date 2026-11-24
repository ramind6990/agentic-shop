import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { toHttpParams } from '../../../core/http/http-params';
import { PageResponse } from '../../../shared/models/api.model';
import { DocumentDetail, DocumentSummary, VersionDetail } from '../models/document.model';

export interface DocumentFilters {
  q?: string;
  type?: string;
  sellerId?: string | number;
}

/** HTTP calls for /api/documents (policies, SOPs, seller contracts, playbooks). Stateless root singleton. */
@Injectable({ providedIn: 'root' })
export class DocumentApi {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = '/api/documents';

  list(
    filters: DocumentFilters,
    page: number,
    size = 50,
  ): Observable<PageResponse<DocumentSummary>> {
    const params = toHttpParams({ ...filters, page, size });
    return this.http.get<PageResponse<DocumentSummary>>(this.baseUrl, { params });
  }

  /** The document with its version list. */
  get(code: string): Observable<DocumentDetail> {
    return this.http.get<DocumentDetail>(`${this.baseUrl}/${encodeURIComponent(code)}`);
  }

  version(code: string, versionNo: number): Observable<VersionDetail> {
    return this.http.get<VersionDetail>(
      `${this.baseUrl}/${encodeURIComponent(code)}/versions/${versionNo}`,
    );
  }

  /** The version in force on a date (yyyy-mm-dd). */
  effective(code: string, at: string): Observable<VersionDetail> {
    const params = toHttpParams({ at });
    return this.http.get<VersionDetail>(`${this.baseUrl}/${encodeURIComponent(code)}/effective`, {
      params,
    });
  }
}
