import { ChangeDetectionStrategy, Component } from '@angular/core';
import { RouterOutlet } from '@angular/router';

import { provideListState } from '../../../../shared/data/list-query';
import { DocumentList } from '../../components/document-list/document-list';
import { DocumentListStore } from '../../state/document-list.store';

/**
 * Documents area: document list on the left, the chosen document (child route) on the right. Both scroll on their
 * own. Provides the list store, so the list keeps its state while the viewer switches documents.
 */
@Component({
  selector: 'app-documents-layout',
  imports: [RouterOutlet, DocumentList],
  providers: [DocumentListStore, provideListState(DocumentListStore)],
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: { class: 'page-fill' },
  template: `
    <div class="row g-4 page-fill-body">
      <div class="col-lg-4 col-xl-3 scroll-pane">
        <app-document-list />
      </div>
      <section class="col-lg-8 col-xl-9 scroll-pane" tabindex="0" aria-label="Document">
        <router-outlet />
      </section>
    </div>
  `,
})
export class DocumentsLayout {}
