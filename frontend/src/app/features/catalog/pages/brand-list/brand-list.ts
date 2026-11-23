import { ChangeDetectionStrategy, Component } from '@angular/core';

import { Ref } from '../../../../shared/models/api.model';
import { ColumnDef } from '../../../../shared/ui/data-table/column-def';
import { DataTable } from '../../../../shared/ui/data-table/data-table';
import { ListLayout } from '../../../../shared/ui/list-layout/list-layout';
import { MOCK_BRAND_PAGE, MOCK_CATEGORY_TREE } from '../../mocks/catalog-reference.mock';

/** Brands (GET /api/brands): search by name, or only brands with products in a category. Mock data for now. */
@Component({
  selector: 'app-brand-list',
  imports: [DataTable, ListLayout],
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: { class: 'd-block h-100' },
  template: `
    <app-list-layout title="Brands" noun="brands" [result]="result">
      <form slot="filters" class="row g-2 align-items-end" role="search" aria-label="Filter brands">
        <div class="col-12 col-md-6 col-xl-4">
          <label for="br-q" class="form-label small mb-1">Name</label>
          <input id="br-q" type="search" class="form-control form-control-sm" placeholder="Novatek" />
        </div>
        <div class="col-8 col-md-4 col-xl-3">
          <label for="br-category" class="form-label small mb-1">Has products in</label>
          <select id="br-category" class="form-select form-select-sm">
            <option value="">Any category</option>
            @for (c of categories; track c.id) {
              <option [value]="c.id">{{ c.name }}</option>
            }
          </select>
        </div>
        <div class="col-4 col-md-2 col-xl-1">
          <button type="reset" class="btn btn-sm btn-outline-secondary w-100">Clear</button>
        </div>
      </form>

      <app-data-table caption="Brands" [rows]="result.content" [columns]="columns" [rowId]="rowId" />
    </app-list-layout>
  `,
})
export class BrandList {
  protected readonly result = MOCK_BRAND_PAGE;
  protected readonly categories = MOCK_CATEGORY_TREE;

  protected readonly rowId = (b: Ref) => b.id;
  protected readonly columns: ColumnDef<Ref>[] = [
    { header: 'Brand', value: (b) => b.name, link: () => ['/products'], hint: (b) => `#${b.id}` },
  ];
}
