import { Injectable, computed, inject } from '@angular/core';

import { injectListQuery } from '../../../shared/data/list-query';
import { loadOnChange } from '../../../shared/data/load-on-change';
import { DocumentApi } from '../data-access/document-api';
import { DOCUMENT_TYPE_LABELS, DOCUMENT_TYPES } from '../models/document.model';

/**
 * Document list on the left of the documents area. Provided by DocumentsLayout, so it survives switching between
 * documents (child routes) and keeps its filters in the URL next to the viewer's own parameters.
 */
@Injectable()
export class DocumentListStore {
  private readonly api = inject(DocumentApi);

  readonly list = injectListQuery(['q', 'type'] as const);
  private readonly result$ = loadOnChange(this.list.query, (q) => this.api.list(q.filters, q.page));

  readonly result = this.result$.value;
  readonly loading = this.result$.loading;
  readonly error = this.result$.error;

  /** Documents of the current page grouped by type, in a fixed type order. */
  readonly groups = computed(() => {
    const documents = this.result()?.content ?? [];
    return DOCUMENT_TYPES.map((type) => ({
      type,
      label: DOCUMENT_TYPE_LABELS[type],
      documents: documents.filter((d) => d.type === type),
    })).filter((g) => g.documents.length > 0);
  });

  reload(): void {
    this.result$.reload();
  }
}
