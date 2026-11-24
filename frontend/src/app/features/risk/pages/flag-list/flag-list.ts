import { ChangeDetectionStrategy, Component, inject } from '@angular/core';

import { provideListState } from '../../../../shared/data/list-query';
import { FilterControl } from '../../../../shared/ui/filter-control/filter-control';
import { ListLayout } from '../../../../shared/ui/list-layout/list-layout';
import { FlagTable } from '../../components/flag-table/flag-table';
import { FlagListStore } from '../../state/flag-list.store';

/** Account flags across customers and sellers (GET /api/flags). Fraud-team data. */
@Component({
  selector: 'app-flag-list',
  imports: [FilterControl, ListLayout, FlagTable],
  providers: [FlagListStore, provideListState(FlagListStore)],
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: { class: 'd-block h-100' },
  template: `
    <app-list-layout
      title="Account flags"
      noun="flags"
      [result]="store.result()"
      [loading]="store.loading()"
      [error]="store.error()"
      (pageChange)="store.list.setPage($event)"
      (retry)="store.reload()"
    >
      <form slot="filters" class="row g-2 align-items-end" role="search" aria-label="Filter flags">
        <div class="col-6 col-md-3 col-xl-2">
          <label for="fl-subject" class="form-label small mb-1">Account type</label>
          <select id="fl-subject" appFilter="subjectType" class="form-select form-select-sm">
            <option value="">Customers and sellers</option>
            <option value="CUSTOMER">Customers</option>
            <option value="SELLER">Sellers</option>
          </select>
        </div>
        <div class="col-6 col-md-3 col-xl-2">
          <label for="fl-subject-id" class="form-label small mb-1">Account ID</label>
          <input
            id="fl-subject-id"
            appFilter="subjectId"
            type="number"
            min="1"
            class="form-control form-control-sm"
            placeholder="3310"
          />
        </div>
        <div class="col-6 col-md-3 col-xl-2">
          <label for="fl-type" class="form-label small mb-1">Flag type</label>
          <select id="fl-type" appFilter="flagType" class="form-select form-select-sm">
            <option value="">Any type</option>
            @for (o of store.typeOptions(); track o.value) {
              <option [value]="o.value">{{ o.label }}</option>
            }
          </select>
        </div>
        <div class="col-6 col-md-3 col-xl-2">
          <label for="fl-active" class="form-label small mb-1">State</label>
          <select id="fl-active" appFilter="active" class="form-select form-select-sm">
            <option value="">Active and closed</option>
            <option value="true">Active</option>
            <option value="false">Closed</option>
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

      <app-flag-table [flags]="store.rows()" [showSubject]="true" />
    </app-list-layout>
  `,
})
export class FlagList {
  protected readonly store = inject(FlagListStore);
}
