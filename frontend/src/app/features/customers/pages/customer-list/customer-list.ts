import { ChangeDetectionStrategy, Component } from '@angular/core';

import { ColumnDef } from '../../../../shared/ui/data-table/column-def';
import { DataTable } from '../../../../shared/ui/data-table/data-table';
import { ListLayout } from '../../../../shared/ui/list-layout/list-layout';
import { MOCK_CUSTOMER_PAGE } from '../../mocks/customer-list.mock';
import { CustomerSummary } from '../../models/customer.model';

/** Customers (GET /api/customers): search by name or email, filter by status. Mock data for now. */
@Component({
  selector: 'app-customer-list',
  imports: [DataTable, ListLayout],
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: { class: 'd-block h-100' },
  template: `
    <app-list-layout title="Customers" noun="customers" [result]="result">
      <form slot="filters" class="row g-2 align-items-end" role="search" aria-label="Filter customers">
        <div class="col-12 col-md-6 col-xl-4">
          <label for="cust-q" class="form-label small mb-1">Name or email</label>
          <input id="cust-q" type="search" class="form-control form-control-sm" placeholder="jordan.king@example.com" />
        </div>
        <div class="col-6 col-md-3 col-xl-2">
          <label for="cust-status" class="form-label small mb-1">Status</label>
          <select id="cust-status" class="form-select form-select-sm">
            <option value="">Any status</option>
            <option value="ACTIVE">Active</option>
            <option value="SUSPENDED">Suspended</option>
          </select>
        </div>
        <div class="col-6 col-md-3 col-xl-1">
          <button type="reset" class="btn btn-sm btn-outline-secondary w-100">Clear</button>
        </div>
      </form>

      <app-data-table caption="Customers" [rows]="result.content" [columns]="columns" [rowId]="rowId" />
    </app-list-layout>
  `,
})
export class CustomerList {
  protected readonly result = MOCK_CUSTOMER_PAGE;

  protected readonly rowId = (c: CustomerSummary) => c.id;
  protected readonly columns: ColumnDef<CustomerSummary>[] = [
    { header: 'Name', value: (c) => c.fullName, link: (c) => ['/customers', c.id], hint: (c) => `#${c.id}` },
    { header: 'Email', value: (c) => c.email },
    { header: 'Status', value: (c) => c.status, type: 'status' },
    { header: 'Customer since', value: (c) => c.createdAt, type: 'date' },
  ];
}
