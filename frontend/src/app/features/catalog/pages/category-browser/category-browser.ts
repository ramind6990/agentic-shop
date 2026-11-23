import { ChangeDetectionStrategy, Component } from '@angular/core';

import { PageHeader } from '../../../../shared/ui/page-header/page-header';
import { CategoryTree } from '../../components/category-tree/category-tree';
import { MOCK_CATEGORY_TREE } from '../../mocks/catalog-reference.mock';

/** The category taxonomy (GET /api/categories), roots open, groups collapsible. Mock data for now. */
@Component({
  selector: 'app-category-browser',
  imports: [PageHeader, CategoryTree],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <app-page-header title="Categories" subtitle="Roots, groups and leaf categories. Products always sit in a leaf." />
    <div class="panel p-3">
      <app-category-tree [nodes]="tree" />
    </div>
  `,
})
export class CategoryBrowser {
  protected readonly tree = MOCK_CATEGORY_TREE;
}
