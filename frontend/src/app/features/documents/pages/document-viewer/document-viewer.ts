import { ChangeDetectionStrategy, Component, computed, inject } from '@angular/core';

import { LoadState } from '../../../../shared/ui/load-state/load-state';
import { NotFoundPanel } from '../../../../shared/ui/not-found-panel/not-found-panel';
import { RoleBadge } from '../../../../shared/ui/role-badge/role-badge';
import { SectionCard } from '../../../../shared/ui/section-card/section-card';
import { DocumentSectionView } from '../../components/document-section-view/document-section-view';
import { VersionTimeline } from '../../components/version-timeline/version-timeline';
import { DOCUMENT_TYPE_LABELS } from '../../models/document.model';
import { DocumentViewerStore } from '../../state/document-viewer.store';
import { AccessLink } from '../../../../shared/ui/access-link/access-link';

/**
 * One document: version picker and "in force on" date (both kept in the URL), the sections of the shown version
 * (each with its minimum role), and the version history.
 */
@Component({
  selector: 'app-document-viewer',
  imports: [
    LoadState,
    NotFoundPanel,
    RoleBadge,
    SectionCard,
    DocumentSectionView,
    VersionTimeline,
    AccessLink,
  ],
  providers: [DocumentViewerStore],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './document-viewer.html',
})
export class DocumentViewer {
  protected readonly store = inject(DocumentViewerStore);

  protected readonly typeLabel = computed(() => {
    const doc = this.store.document();
    return doc ? DOCUMENT_TYPE_LABELS[doc.type] : '';
  });

  protected onVersion(event: Event): void {
    this.store.selectVersion(Number((event.target as HTMLSelectElement).value));
  }

  protected onShowAt(event: Event, date: string): void {
    event.preventDefault();
    if (date) {
      this.store.showInForceOn(date);
    }
  }
}
