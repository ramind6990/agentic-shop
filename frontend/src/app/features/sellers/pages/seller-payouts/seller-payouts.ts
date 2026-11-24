import { ChangeDetectionStrategy, Component, inject } from '@angular/core';

import { ColumnDef } from '../../../../shared/ui/data-table/column-def';
import { DataTable } from '../../../../shared/ui/data-table/data-table';
import { PagedSection } from '../../../../shared/ui/paged-section/paged-section';
import { Payout } from '../../models/seller.model';
import { SellerPayoutsStore } from '../../state/seller-detail.store';

/** Seller payouts tab: bi-weekly settlements, newest first. Net = gross − commission − fees − refunds. */
@Component({
  selector: 'app-seller-payouts',
  imports: [DataTable, PagedSection],
  providers: [SellerPayoutsStore],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @let payouts = store.payouts;
    <app-paged-section
      title="Payouts"
      [result]="payouts.value()"
      [loading]="payouts.loading()"
      [error]="payouts.error()"
      (pageChange)="payouts.setPage($event)"
      (retry)="payouts.reload()"
    >
      <span slot="actions" class="small text-body-secondary">
        Net = gross − commission − fees − refunds
      </span>
      <app-data-table
        caption="Seller payouts"
        emptyText="No payouts."
        [rows]="payouts.rows()"
        [columns]="columns"
        [rowId]="rowId"
      />
    </app-paged-section>
  `,
})
export class SellerPayouts {
  protected readonly store = inject(SellerPayoutsStore);

  protected readonly rowId = (p: Payout) => p.id;
  protected readonly columns: ColumnDef<Payout>[] = [
    {
      header: 'Period',
      value: (p) => p.periodStart,
      type: 'date',
      hint: (p) => `to ${p.periodEnd}`,
    },
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
