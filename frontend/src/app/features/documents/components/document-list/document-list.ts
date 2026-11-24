import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { RouterLink, RouterLinkActive } from '@angular/router';

import { FilterControl } from '../../../../shared/ui/filter-control/filter-control';
import { LoadState } from '../../../../shared/ui/load-state/load-state';
import { Pager } from '../../../../shared/ui/pager/pager';
import { DOCUMENT_TYPE_LABELS, DOCUMENT_TYPES } from '../../models/document.model';
import { DocumentListStore } from '../../state/document-list.store';

/** Documents grouped by type, with search and a type filter on top (kept in the URL). */
@Component({
  selector: 'app-document-list',
  imports: [RouterLink, RouterLinkActive, FilterControl, LoadState, Pager],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './document-list.html',
  styleUrl: './document-list.scss',
})
export class DocumentList {
  protected readonly store = inject(DocumentListStore);

  protected readonly typeOptions = DOCUMENT_TYPES.map((value) => ({
    value,
    label: DOCUMENT_TYPE_LABELS[value],
  }));

  /** Opening a document keeps the list filters but drops the previous document's version choice. */
  protected readonly openParams = { version: null, at: null };
}
