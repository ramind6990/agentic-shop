import { ChangeDetectionStrategy, Component, inject } from '@angular/core';

import { ColumnDef } from '../../../../shared/ui/data-table/column-def';
import { DataTable } from '../../../../shared/ui/data-table/data-table';
import { LoadState } from '../../../../shared/ui/load-state/load-state';
import { SectionCard } from '../../../../shared/ui/section-card/section-card';
import { SellerContract } from '../../models/seller.model';
import { SellerDetailStore } from '../../state/seller-detail.store';

/** Seller contracts tab: fee agreements over time, newest first. Finance data, loaded by the page frame. */
@Component({
  selector: 'app-seller-contracts',
  imports: [DataTable, LoadState, SectionCard],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <app-section-card title="Fee agreements" [flush]="true">
      <app-load-state
        [loading]="store.contractsLoading()"
        [error]="store.contractsError()"
        [hasValue]="!store.contractsLoading() || store.contracts().length > 0"
        (retry)="store.reloadContracts()"
      >
        <app-data-table
          caption="Seller contracts"
          emptyText="No contracts."
          [rows]="store.contracts()"
          [columns]="columns"
          [rowId]="rowId"
        />
      </app-load-state>
    </app-section-card>
  `,
})
export class SellerContracts {
  protected readonly store = inject(SellerDetailStore);

  protected readonly rowId = (c: SellerContract) => c.id;
  protected readonly columns: ColumnDef<SellerContract>[] = [
    { header: 'Plan', value: (c) => c.feePlan, type: 'status' },
    { header: 'From', value: (c) => c.effectiveFrom, type: 'date' },
    {
      header: 'To',
      value: (c) => c.effectiveTo,
      type: 'date',
      hint: (c) => (c.effectiveTo ? null : 'Current'),
    },
    { header: 'Commission', value: (c) => `${(c.commissionRate * 100).toFixed(1)}%` },
    { header: 'Per order', value: (c) => c.perOrderFee, type: 'money' },
    { header: 'Monthly', value: (c) => c.monthlyFee, type: 'money' },
    { header: 'Return window', value: (c) => `${c.returnWindowDays} days` },
    {
      header: 'Document',
      value: (c) => c.documentCode,
      link: (c) => ['/documents', c.documentCode],
      mono: true,
    },
  ];
}
