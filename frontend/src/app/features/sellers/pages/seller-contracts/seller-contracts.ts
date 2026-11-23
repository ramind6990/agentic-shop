import { ChangeDetectionStrategy, Component } from '@angular/core';

import { ColumnDef } from '../../../../shared/ui/data-table/column-def';
import { DataTable } from '../../../../shared/ui/data-table/data-table';
import { SectionCard } from '../../../../shared/ui/section-card/section-card';
import { MOCK_SELLER_CONTRACTS } from '../../mocks/seller-detail.mock';
import { SellerContract } from '../../models/seller.model';

/** Seller contracts tab: fee agreements over time, newest first. Finance data. */
@Component({
  selector: 'app-seller-contracts',
  imports: [DataTable, SectionCard],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <app-section-card title="Fee agreements" [flush]="true">
      <app-data-table caption="Seller contracts" [rows]="contracts" [columns]="columns" [rowId]="rowId" />
    </app-section-card>
  `,
})
export class SellerContracts {
  protected readonly contracts = MOCK_SELLER_CONTRACTS;
  protected readonly rowId = (c: SellerContract) => c.id;
  protected readonly columns: ColumnDef<SellerContract>[] = [
    { header: 'Plan', value: (c) => c.feePlan, type: 'status' },
    { header: 'From', value: (c) => c.effectiveFrom, type: 'date' },
    { header: 'To', value: (c) => c.effectiveTo, type: 'date', hint: (c) => (c.effectiveTo ? null : 'Current') },
    { header: 'Commission', value: (c) => `${(c.commissionRate * 100).toFixed(1)}%` },
    { header: 'Per order', value: (c) => c.perOrderFee, type: 'money' },
    { header: 'Monthly', value: (c) => c.monthlyFee, type: 'money' },
    { header: 'Return window', value: (c) => `${c.returnWindowDays} days` },
    { header: 'Document', value: (c) => c.documentCode, link: (c) => ['/documents', c.documentCode], mono: true },
  ];
}
