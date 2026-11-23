import { ChangeDetectionStrategy, Component } from '@angular/core';
import { RouterOutlet } from '@angular/router';

import { DocumentList } from '../../components/document-list/document-list';
import { MOCK_DOCUMENTS } from '../../mocks/documents.mock';

/** Documents area: document list on the left, the chosen document (child route) on the right. Both scroll on their own. */
@Component({
  selector: 'app-documents-layout',
  imports: [RouterOutlet, DocumentList],
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: { class: 'page-fill' },
  template: `
    <div class="row g-4 page-fill-body">
      <div class="col-lg-4 col-xl-3 scroll-pane">
        <app-document-list [documents]="documents" />
      </div>
      <section class="col-lg-8 col-xl-9 scroll-pane" tabindex="0" aria-label="Document">
        <router-outlet />
      </section>
    </div>
  `,
})
export class DocumentsLayout {
  protected readonly documents = MOCK_DOCUMENTS;
}
