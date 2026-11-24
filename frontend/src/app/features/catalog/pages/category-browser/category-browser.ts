import { ChangeDetectionStrategy, Component, inject } from '@angular/core';

import { LoadState } from '../../../../shared/ui/load-state/load-state';
import { PageHeader } from '../../../../shared/ui/page-header/page-header';
import { CategoryTree } from '../../components/category-tree/category-tree';
import { CategoryTreeStore } from '../../state/category-tree.store';

/** The category taxonomy (GET /api/categories), roots open, groups collapsible. Leaves open the product search. */
@Component({
  selector: 'app-category-browser',
  imports: [LoadState, PageHeader, CategoryTree],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <app-page-header
      title="Categories"
      subtitle="Roots, groups and leaf categories. Products always sit in a leaf."
    />
    <div class="panel p-3">
      <app-load-state
        [loading]="store.loading()"
        [error]="store.error()"
        [hasValue]="!!store.tree()"
        (retry)="store.reload()"
      >
        @if (store.tree(); as tree) {
          <app-category-tree [nodes]="tree" />
        }
      </app-load-state>
    </div>
  `,
})
export class CategoryBrowser {
  protected readonly store = inject(CategoryTreeStore);
}
