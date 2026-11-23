import { ChangeDetectionStrategy, Component } from '@angular/core';
import { Routes } from '@angular/router';

/** Shown in the documents area before a document is chosen. */
@Component({
  selector: 'app-document-none',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="panel p-5 text-center text-body-secondary">
      <h1 class="h5 text-body">Documents</h1>
      <p class="mb-0">Choose a policy, SOP, contract or playbook from the list.</p>
    </div>
  `,
})
class DocumentNone {}

export const DOCUMENT_ROUTES: Routes = [
  {
    path: '',
    loadComponent: () =>
      import('./pages/documents-layout/documents-layout').then((m) => m.DocumentsLayout),
    children: [
      { path: '', title: 'Documents · AgenticShop', component: DocumentNone },
      {
        path: ':code',
        title: 'Document · AgenticShop',
        loadComponent: () =>
          import('./pages/document-viewer/document-viewer').then((m) => m.DocumentViewer),
      },
    ],
  },
];
