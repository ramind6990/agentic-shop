import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';

import { ColumnDef } from '../../../../shared/ui/data-table/column-def';
import { DataTable } from '../../../../shared/ui/data-table/data-table';
import { enumLabel } from '../../../../shared/utils/enum-label';
import { AccountFlag } from '../../models/flag.model';

const SUBJECT_COLUMN: ColumnDef<AccountFlag> = {
  header: 'Account',
  value: (f) => `${f.subjectType === 'CUSTOMER' ? 'Customer' : 'Seller'} #${f.subjectId}`,
  link: (f) => [f.subjectType === 'CUSTOMER' ? '/customers' : '/sellers', f.subjectId],
};

const BASE_COLUMNS: ColumnDef<AccountFlag>[] = [
  { header: 'Type', value: (f) => enumLabel(f.flagType), hint: (f) => f.note },
  { header: 'Risk score', value: (f) => f.riskScore, type: 'number' },
  { header: 'State', value: (f) => (f.active ? 'ACTIVE' : 'CLOSED'), type: 'status' },
  { header: 'Raised by', value: (f) => f.createdBy?.name ?? null },
  { header: 'Raised', value: (f) => f.createdAt, type: 'date' },
];

/** Account flags with risk score and note. Fraud-team data: later shown only to the FRAUD role. */
@Component({
  selector: 'app-flag-table',
  imports: [DataTable],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <app-data-table
      caption="Account flags"
      [emptyText]="showSubject() ? 'No flags match these filters.' : 'No flags on this account.'"
      [rows]="flags()"
      [columns]="columns()"
      [rowId]="rowId"
    />
  `,
})
export class FlagTable {
  readonly flags = input.required<AccountFlag[]>();
  /** Add the flagged account as the first column (on the all-flags list). */
  readonly showSubject = input(false);

  protected readonly rowId = (f: AccountFlag) => f.id;
  protected readonly columns = computed(() =>
    this.showSubject() ? [SUBJECT_COLUMN, ...BASE_COLUMNS] : BASE_COLUMNS,
  );
}
