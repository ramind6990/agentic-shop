import { ChangeDetectionStrategy, Component, inject } from '@angular/core';

import { provideListState } from '../../../../shared/data/list-query';
import { ColumnDef } from '../../../../shared/ui/data-table/column-def';
import { DataTable } from '../../../../shared/ui/data-table/data-table';
import { FilterControl } from '../../../../shared/ui/filter-control/filter-control';
import { ListLayout } from '../../../../shared/ui/list-layout/list-layout';
import { CustomerSummary } from '../../models/customer.model';
import { CustomerListStore } from '../../state/customer-list.store';

/** Customers (GET /api/customers): search by name or email, filter by status. */
@Component({
  selector: 'app-customer-list',
  imports: [DataTable, FilterControl, ListLayout],
  providers: [CustomerListStore, provideListState(CustomerListStore)],
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: { class: 'd-block h-100' },
  template: `
    <app-list-layout
      title="Customers"
      noun="customers"
      [result]="store.result()"
      [loading]="store.loading()"
      [error]="store.error()"
      (pageChange)="store.list.setPage($event)"
      (retry)="store.reload()"
    >
      <form
        slot="filters"
        class="row g-2 align-items-end"
        role="search"
        aria-label="Filter customers"
      >
        <div class="col-12 col-md-6 col-xl-4">
          <label for="cust-q" class="form-label small mb-1">Name or email</label>
          <input
            id="cust-q"
            appFilter="q"
            type="search"
            class="form-control form-control-sm"
            placeholder="jordan.king@example.com"
          />
        </div>
        <div class="col-6 col-md-3 col-xl-2">
          <label for="cust-status" class="form-label small mb-1">Status</label>
          <select id="cust-status" appFilter="status" class="form-select form-select-sm">
            <option value="">Any status</option>
            @for (o of store.statusOptions(); track o.value) {
              <option [value]="o.value">{{ o.label }}</option>
            }
          </select>
        </div>
        <div class="col-6 col-md-3 col-xl-1">
          <button
            type="button"
            class="btn btn-sm btn-outline-secondary w-100"
            [disabled]="!store.list.hasFilters()"
            (click)="store.list.clear()"
          >
            Clear
          </button>
        </div>
      </form>

      <app-data-table
        caption="Customers"
        [rows]="store.rows()"
        [columns]="columns"
        [rowId]="rowId"
      />
    </app-list-layout>
  `,
})
export class CustomerList {
  protected readonly store = inject(CustomerListStore);

  protected readonly rowId = (c: CustomerSummary) => c.id;
  protected readonly columns: ColumnDef<CustomerSummary>[] = [
    {
      header: 'Name',
      value: (c) => c.fullName,
      link: (c) => ['/customers', c.id],
      hint: (c) => `#${c.id}`,
    },
    { header: 'Email', value: (c) => c.email },
    { header: 'Status', value: (c) => c.status, type: 'status' },
    { header: 'Customer since', value: (c) => c.createdAt, type: 'date' },
  ];
}
