import { ChangeDetectionStrategy, Component } from '@angular/core';

import { SectionCard } from '../../../../shared/ui/section-card/section-card';
import { FlagTable } from '../../../risk/components/flag-table/flag-table';
import { flagsFor } from '../../../risk/mocks/flags.mock';
import { MOCK_SELLER } from '../../mocks/seller-detail.mock';

/** Seller flags tab (GET /api/flags?subjectType=SELLER&subjectId=). */
@Component({
  selector: 'app-seller-flags',
  imports: [SectionCard, FlagTable],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <app-section-card title="Account flags" [flush]="true">
      <app-flag-table [flags]="flags" />
    </app-section-card>
  `,
})
export class SellerFlags {
  protected readonly flags = flagsFor('SELLER', MOCK_SELLER.id);
}
