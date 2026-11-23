import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';
import { RouterLink, RouterLinkActive } from '@angular/router';

import { DOCUMENT_TYPE_LABELS, DOCUMENT_TYPES, DocumentSummary } from '../../models/document.model';

/** Documents grouped by type, with a type filter and search on top. Display-only filters for now. */
@Component({
  selector: 'app-document-list',
  imports: [RouterLink, RouterLinkActive],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './document-list.html',
  styleUrl: './document-list.scss',
})
export class DocumentList {
  readonly documents = input.required<DocumentSummary[]>();

  protected readonly typeOptions = DOCUMENT_TYPES.map((value) => ({ value, label: DOCUMENT_TYPE_LABELS[value] }));

  protected readonly groups = computed(() =>
    DOCUMENT_TYPES.map((type) => ({
      type,
      label: DOCUMENT_TYPE_LABELS[type],
      documents: this.documents().filter((d) => d.type === type),
    })).filter((g) => g.documents.length > 0),
  );
}
