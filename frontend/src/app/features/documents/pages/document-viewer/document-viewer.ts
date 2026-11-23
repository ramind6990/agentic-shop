import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';
import { RouterLink } from '@angular/router';

import { RoleBadge } from '../../../../shared/ui/role-badge/role-badge';
import { SectionCard } from '../../../../shared/ui/section-card/section-card';
import { DocumentSectionView } from '../../components/document-section-view/document-section-view';
import { VersionTimeline } from '../../components/version-timeline/version-timeline';
import { mockDocument } from '../../mocks/documents.mock';
import { DOCUMENT_TYPE_LABELS } from '../../models/document.model';

/**
 * One document: version picker and "in force on" date, the sections of the shown version
 * (each with its minimum role), and the version history. Shows the current version for now.
 */
@Component({
  selector: 'app-document-viewer',
  imports: [RouterLink, RoleBadge, SectionCard, DocumentSectionView, VersionTimeline],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './document-viewer.html',
})
export class DocumentViewer {
  /** Route parameter. */
  readonly code = input<string>();

  private readonly mock = computed(() => mockDocument(this.code()));
  protected readonly doc = computed(() => this.mock().detail);
  protected readonly version = computed(() => this.mock().versions[0]);
  protected readonly typeLabel = computed(() => DOCUMENT_TYPE_LABELS[this.doc().type]);
}
