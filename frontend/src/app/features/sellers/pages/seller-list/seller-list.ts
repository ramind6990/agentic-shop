import { ChangeDetectionStrategy, Component } from '@angular/core';

import { ColumnDef } from '../../../../shared/ui/data-table/column-def';
import { DataTable } from '../../../../shared/ui/data-table/data-table';
import { ListLayout } from '../../../../shared/ui/list-layout/list-layout';
import { MOCK_SELLER_PAGE } from '../../mocks/seller-list.mock';
import { SellerSummary } from '../../models/seller.model';

/** Sellers (GET /api/sellers): search by name, filter by status and country. Mock data for now. */
@Component({
  selector: 'app-seller-list',
  imports: [DataTable, ListLayout],
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: { class: 'd-block h-100' },
  template: `
    <app-list-layout title="Sellers" noun="sellers" [result]="result">
      <form slot="filters" class="row g-2 align-items-end" role="search" aria-label="Filter sellers">
        <div class="col-12 col-md-6 col-xl-4">
          <label for="sel-q" class="form-label small mb-1">Name</label>
          <input id="sel-q" type="search" class="form-control form-control-sm" placeholder="Maple Street" />
        </div>
        <div class="col-6 col-md-3 col-xl-2">
          <label for="sel-status" class="form-label small mb-1">Status</label>
          <select id="sel-status" class="form-select form-select-sm">
            <option value="">Any status</option>
            <option value="ACTIVE">Active</option>
            <option value="SUSPENDED">Suspended</option>
            <option value="OFFBOARDED">Offboarded</option>
          </select>
        </div>
        <div class="col-6 col-md-3 col-xl-2">
          <label for="sel-country" class="form-label small mb-1">Country</label>
          <select id="sel-country" class="form-select form-select-sm">
            <option value="">Any country</option>
            @for (c of countries; track c.code) {
              <option [value]="c.code">{{ c.name }}</option>
            }
          </select>
        </div>
        <div class="col-6 col-md-3 col-xl-1">
          <button type="reset" class="btn btn-sm btn-outline-secondary w-100">Clear</button>
        </div>
      </form>

      <app-data-table caption="Sellers" [rows]="result.content" [columns]="columns" [rowId]="rowId" />
    </app-list-layout>
  `,
})
export class SellerList {
  protected readonly result = MOCK_SELLER_PAGE;
  /** Countries in the seed data. */
  protected readonly countries = [
    { code: 'US', name: 'United States' },
    { code: 'GB', name: 'United Kingdom' },
    { code: 'CA', name: 'Canada' },
    { code: 'DE', name: 'Germany' },
  ];

  protected readonly rowId = (s: SellerSummary) => s.id;
  protected readonly columns: ColumnDef<SellerSummary>[] = [
    { header: 'Seller', value: (s) => s.name, link: (s) => ['/sellers', s.id], hint: (s) => s.slug },
    { header: 'Country', value: (s) => s.country },
    { header: 'Rating', value: (s) => s.ratingAvg.toFixed(1) },
    { header: 'Status', value: (s) => s.status, type: 'status' },
    { header: 'Joined', value: (s) => s.joinedAt, type: 'date' },
  ];
}
