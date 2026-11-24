import { ChangeDetectionStrategy, Component, inject } from '@angular/core';

import { PagedSection } from '../../../../shared/ui/paged-section/paged-section';
import { FlagTable } from '../../../risk/components/flag-table/flag-table';
import { CustomerFlagsStore } from '../../state/customer-detail.store';

/** Customer flags tab (GET /api/flags?subjectType=CUSTOMER&subjectId=). */
@Component({
  selector: 'app-customer-flags',
  imports: [PagedSection, FlagTable],
  providers: [CustomerFlagsStore],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @let flags = store.flags;
    <app-paged-section
      title="Account flags"
      [result]="flags.value()"
      [loading]="flags.loading()"
      [error]="flags.error()"
      (pageChange)="flags.setPage($event)"
      (retry)="flags.reload()"
    >
      <app-flag-table [flags]="flags.rows()" />
    </app-paged-section>
  `,
})
export class CustomerFlags {
  protected readonly store = inject(CustomerFlagsStore);
}
