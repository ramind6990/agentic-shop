import { ChangeDetectionStrategy, Component } from '@angular/core';

import { ColumnDef } from '../../../../shared/ui/data-table/column-def';
import { DataTable } from '../../../../shared/ui/data-table/data-table';
import { SectionCard } from '../../../../shared/ui/section-card/section-card';
import { MOCK_SELLER_PAYOUTS } from '../../mocks/seller-detail.mock';
import { Payout } from '../../models/seller.model';

/** Seller payouts tab: bi-weekly settlements, newest first. Net = gross − commission − fees − refunds. */
@Component({
  selector: 'app-seller-payouts',
  imports: [DataTable, SectionCard],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <app-section-card title="Payouts" [flush]="true">
      <span slot="actions" class="small text-body-secondary">Net = gross − commission − fees − refunds</span>
      <app-data-table caption="Seller payouts" [rows]="payouts" [columns]="columns" [rowId]="rowId" />
    </app-section-card>
  `,
})
export class SellerPayouts {
  protected readonly payouts = MOCK_SELLER_PAYOUTS;
  protected readonly rowId = (p: Payout) => p.id;
  protected readonly columns: ColumnDef<Payout>[] = [
    { header: 'Period', value: (p) => p.periodStart, type: 'date', hint: (p) => `to ${p.periodEnd}` },
    { header: 'Orders', value: (p) => p.orderCount, type: 'number' },
    { header: 'Gross', value: (p) => p.grossSales, type: 'money' },
    { header: 'Commission', value: (p) => p.commissionAmount, type: 'money' },
    { header: 'Fees', value: (p) => p.feesAmount, type: 'money' },
    { header: 'Refunds', value: (p) => p.refundsAmount, type: 'money' },
    { header: 'Net', value: (p) => p.netAmount, type: 'money' },
    { header: 'Status', value: (p) => p.status, type: 'status' },
    { header: 'Paid', value: (p) => p.paidAt, type: 'date' },
  ];
}
