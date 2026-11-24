import { ChangeDetectionStrategy, Component, inject } from '@angular/core';

import { provideListState } from '../../../../shared/data/list-query';
import { Ref } from '../../../../shared/models/api.model';
import { ColumnDef } from '../../../../shared/ui/data-table/column-def';
import { DataTable } from '../../../../shared/ui/data-table/data-table';
import { FilterControl } from '../../../../shared/ui/filter-control/filter-control';
import { ListLayout } from '../../../../shared/ui/list-layout/list-layout';
import { BrandListStore } from '../../state/brand-list.store';
import { CategoryTreeStore } from '../../state/category-tree.store';

/** Brands (GET /api/brands): search by name, or only brands with products in a category. */
@Component({
  selector: 'app-brand-list',
  imports: [DataTable, FilterControl, ListLayout],
  providers: [BrandListStore, provideListState(BrandListStore)],
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: { class: 'd-block h-100' },
  template: `
    <app-list-layout
      title="Brands"
      noun="brands"
      [result]="store.result()"
      [loading]="store.loading()"
      [error]="store.error()"
      (pageChange)="store.list.setPage($event)"
      (retry)="store.reload()"
    >
      <form slot="filters" class="row g-2 align-items-end" role="search" aria-label="Filter brands">
        <div class="col-12 col-md-6 col-xl-4">
          <label for="br-q" class="form-label small mb-1">Name</label>
          <input
            id="br-q"
            appFilter="q"
            type="search"
            class="form-control form-control-sm"
            placeholder="Novatek"
          />
        </div>
        <div class="col-8 col-md-4 col-xl-3">
          <label for="br-category" class="form-label small mb-1">Has products in</label>
          <select id="br-category" appFilter="categoryId" class="form-select form-select-sm">
            <option value="">Any category</option>
            @for (c of categories.options(); track c.id) {
              <option [value]="c.id">{{ c.label }}</option>
            }
          </select>
        </div>
        <div class="col-4 col-md-2 col-xl-1">
          <button
            type="button"
            class="btn btn-sm btn-outline-secondary w-100"
            (click)="store.list.clear()"
          >
            Clear
          </button>
        </div>
      </form>

      <app-data-table caption="Brands" [rows]="store.rows()" [columns]="columns" [rowId]="rowId" />
    </app-list-layout>
  `,
})
export class BrandList {
  protected readonly store = inject(BrandListStore);
  protected readonly categories = inject(CategoryTreeStore);

  protected readonly rowId = (b: Ref) => b.id;
  protected readonly columns: ColumnDef<Ref>[] = [
    {
      header: 'Brand',
      value: (b) => b.name,
      link: () => ['/products'],
      linkQuery: (b) => ({ brandIds: b.id }),
      hint: (b) => `#${b.id}`,
    },
  ];
}
