import { ChangeDetectionStrategy, Component, inject } from '@angular/core';

import { provideListState } from '../../../../shared/data/list-query';
import { ColumnDef } from '../../../../shared/ui/data-table/column-def';
import { DataTable } from '../../../../shared/ui/data-table/data-table';
import { FilterControl } from '../../../../shared/ui/filter-control/filter-control';
import { ListLayout } from '../../../../shared/ui/list-layout/list-layout';
import { StaffUser } from '../../models/staff-user.model';
import { StaffListStore } from '../../state/staff-list.store';

/** Staff and seller accounts with their roles (GET /api/users). These become the session identities later. */
@Component({
  selector: 'app-staff-list',
  imports: [DataTable, FilterControl, ListLayout],
  providers: [StaffListStore, provideListState(StaffListStore)],
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: { class: 'd-block h-100' },
  template: `
    <app-list-layout
      title="Staff users"
      noun="accounts"
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
        aria-label="Filter staff users"
      >
        <div class="col-12 col-md-6 col-xl-4">
          <label for="usr-q" class="form-label small mb-1">Name or email</label>
          <input
            id="usr-q"
            appFilter="q"
            type="search"
            class="form-control form-control-sm"
            placeholder="Dana Whitfield"
          />
        </div>
        <div class="col-6 col-md-3 col-xl-2">
          <label for="usr-role" class="form-label small mb-1">Role</label>
          <select id="usr-role" appFilter="role" class="form-select form-select-sm">
            <option value="">Any role</option>
            @for (o of store.roleOptions(); track o.value) {
              <option [value]="o.value">{{ o.label }}</option>
            }
          </select>
        </div>
        <div class="col-6 col-md-3 col-xl-2">
          <label for="usr-seller" class="form-label small mb-1">Seller ID</label>
          <input
            id="usr-seller"
            appFilter="sellerId"
            type="number"
            min="1"
            class="form-control form-control-sm"
            placeholder="171"
          />
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
        caption="Staff and seller accounts"
        [rows]="store.rows()"
        [columns]="columns"
        [rowId]="rowId"
      />
    </app-list-layout>
  `,
})
export class StaffList {
  protected readonly store = inject(StaffListStore);

  protected readonly rowId = (u: StaffUser) => u.id;
  protected readonly columns: ColumnDef<StaffUser>[] = [
    { header: 'Name', value: (u) => u.fullName, hint: (u) => `#${u.id}` },
    { header: 'Email', value: (u) => u.email },
    { header: 'Role', value: (u) => u.role, type: 'role' },
    {
      header: 'Seller',
      value: (u) => u.seller?.name ?? null,
      link: (u) => ['/sellers', u.seller?.id ?? ''],
    },
    { header: 'Status', value: (u) => u.status, type: 'status' },
    { header: 'Created', value: (u) => u.createdAt, type: 'date' },
  ];
}
