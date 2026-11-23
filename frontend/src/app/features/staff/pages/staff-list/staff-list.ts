import { ChangeDetectionStrategy, Component } from '@angular/core';

import { MOCK_SELLER_REFS } from '../../../../shared/mocks/reference.mock';
import { ColumnDef } from '../../../../shared/ui/data-table/column-def';
import { DataTable } from '../../../../shared/ui/data-table/data-table';
import { ListLayout } from '../../../../shared/ui/list-layout/list-layout';
import { enumOptions } from '../../../../shared/utils/enum-label';
import { MOCK_STAFF_PAGE } from '../../mocks/staff-list.mock';
import { STAFF_ROLES, StaffUser } from '../../models/staff-user.model';

/** Staff and seller accounts with their roles (GET /api/users). These become the session identities later. */
@Component({
  selector: 'app-staff-list',
  imports: [DataTable, ListLayout],
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: { class: 'd-block h-100' },
  template: `
    <app-list-layout title="Staff users" noun="accounts" [result]="result">
      <form slot="filters" class="row g-2 align-items-end" role="search" aria-label="Filter staff users">
        <div class="col-12 col-md-6 col-xl-4">
          <label for="usr-q" class="form-label small mb-1">Name or email</label>
          <input id="usr-q" type="search" class="form-control form-control-sm" placeholder="Dana Whitfield" />
        </div>
        <div class="col-6 col-md-3 col-xl-2">
          <label for="usr-role" class="form-label small mb-1">Role</label>
          <select id="usr-role" class="form-select form-select-sm">
            <option value="">Any role</option>
            @for (o of roleOptions; track o.value) {
              <option [value]="o.value">{{ o.label }}</option>
            }
          </select>
        </div>
        <div class="col-6 col-md-3 col-xl-3">
          <label for="usr-seller" class="form-label small mb-1">Seller</label>
          <select id="usr-seller" class="form-select form-select-sm">
            <option value="">Any seller</option>
            @for (s of sellers; track s.id) {
              <option [value]="s.id">{{ s.name }}</option>
            }
          </select>
        </div>
        <div class="col-6 col-md-3 col-xl-1">
          <button type="reset" class="btn btn-sm btn-outline-secondary w-100">Clear</button>
        </div>
      </form>

      <app-data-table caption="Staff and seller accounts" [rows]="result.content" [columns]="columns" [rowId]="rowId" />
    </app-list-layout>
  `,
})
export class StaffList {
  protected readonly result = MOCK_STAFF_PAGE;
  protected readonly sellers = MOCK_SELLER_REFS;
  protected readonly roleOptions = enumOptions(STAFF_ROLES);

  protected readonly rowId = (u: StaffUser) => u.id;
  protected readonly columns: ColumnDef<StaffUser>[] = [
    { header: 'Name', value: (u) => u.fullName, hint: (u) => `#${u.id}` },
    { header: 'Email', value: (u) => u.email },
    { header: 'Role', value: (u) => u.role, type: 'role' },
    { header: 'Seller', value: (u) => u.seller?.name ?? null, link: (u) => ['/sellers', u.seller?.id ?? ''] },
    { header: 'Status', value: (u) => u.status, type: 'status' },
    { header: 'Created', value: (u) => u.createdAt, type: 'date' },
  ];
}
