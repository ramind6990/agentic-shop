import { ChangeDetectionStrategy, Component, inject } from '@angular/core';

import { PagedSection } from '../../../../shared/ui/paged-section/paged-section';
import { FlagTable } from '../../../risk/components/flag-table/flag-table';
import { SellerFlagsStore } from '../../state/seller-detail.store';

/** Seller flags tab (GET /api/flags?subjectType=SELLER&subjectId=). */
@Component({
  selector: 'app-seller-flags',
  imports: [PagedSection, FlagTable],
  providers: [SellerFlagsStore],
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
export class SellerFlags {
  protected readonly store = inject(SellerFlagsStore);
}
