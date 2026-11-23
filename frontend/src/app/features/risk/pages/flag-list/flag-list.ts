import { ChangeDetectionStrategy, Component } from '@angular/core';

import { ListLayout } from '../../../../shared/ui/list-layout/list-layout';
import { enumOptions } from '../../../../shared/utils/enum-label';
import { FlagTable } from '../../components/flag-table/flag-table';
import { MOCK_FLAG_PAGE } from '../../mocks/flags.mock';
import { FLAG_TYPES } from '../../models/flag.model';

/** Account flags across customers and sellers (GET /api/flags). Fraud-team data. Mock data for now. */
@Component({
  selector: 'app-flag-list',
  imports: [ListLayout, FlagTable],
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: { class: 'd-block h-100' },
  template: `
    <app-list-layout title="Account flags" noun="flags" [result]="result">
      <form slot="filters" class="row g-2 align-items-end" role="search" aria-label="Filter flags">
        <div class="col-6 col-md-3 col-xl-2">
          <label for="fl-subject" class="form-label small mb-1">Account type</label>
          <select id="fl-subject" class="form-select form-select-sm">
            <option value="">Customers and sellers</option>
            <option value="CUSTOMER">Customers</option>
            <option value="SELLER">Sellers</option>
          </select>
        </div>
        <div class="col-6 col-md-3 col-xl-2">
          <label for="fl-subject-id" class="form-label small mb-1">Account ID</label>
          <input id="fl-subject-id" type="number" class="form-control form-control-sm" placeholder="3310" />
        </div>
        <div class="col-6 col-md-3 col-xl-2">
          <label for="fl-type" class="form-label small mb-1">Flag type</label>
          <select id="fl-type" class="form-select form-select-sm">
            <option value="">Any type</option>
            @for (o of typeOptions; track o.value) {
              <option [value]="o.value">{{ o.label }}</option>
            }
          </select>
        </div>
        <div class="col-6 col-md-3 col-xl-2">
          <label for="fl-active" class="form-label small mb-1">State</label>
          <select id="fl-active" class="form-select form-select-sm">
            <option value="">Active and closed</option>
            <option value="true">Active</option>
            <option value="false">Closed</option>
          </select>
        </div>
        <div class="col-6 col-md-3 col-xl-1">
          <button type="reset" class="btn btn-sm btn-outline-secondary w-100">Clear</button>
        </div>
      </form>

      <app-flag-table [flags]="result.content" [showSubject]="true" />
    </app-list-layout>
  `,
})
export class FlagList {
  protected readonly result = MOCK_FLAG_PAGE;
  protected readonly typeOptions = enumOptions(FLAG_TYPES);
}
