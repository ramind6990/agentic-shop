import { ChangeDetectionStrategy, Component, input } from '@angular/core';

import { RichText } from '../../../../shared/ui/rich-text/rich-text';
import { RoleBadge } from '../../../../shared/ui/role-badge/role-badge';
import { DocumentSection } from '../../models/document.model';

/** One numbered section with its own minimum role (section-level access control). */
@Component({
  selector: 'app-document-section-view',
  imports: [RichText, RoleBadge],
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: { class: 'd-block' },
  template: `
    <section [attr.aria-labelledby]="'section-' + section().ordinal">
      <div class="d-flex flex-wrap align-items-center gap-2 mb-2">
        <h3 class="h6 mb-0" [id]="'section-' + section().ordinal">
          <span class="text-body-secondary num me-1">{{ section().ordinal }}.</span>{{ section().heading }}
        </h3>
        <app-role-badge class="ms-auto" prefix="Min role:" [role]="section().minRole" />
      </div>
      <app-rich-text class="small" [text]="section().body" />
    </section>
  `,
})
export class DocumentSectionView {
  readonly section = input.required<DocumentSection>();
}
