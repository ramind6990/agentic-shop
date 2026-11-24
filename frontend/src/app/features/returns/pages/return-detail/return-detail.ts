import { ChangeDetectionStrategy, Component, computed, inject } from '@angular/core';
import { RouterLink } from '@angular/router';

import { Breadcrumbs, Crumb } from '../../../../shared/ui/breadcrumbs/breadcrumbs';
import { ColumnDef } from '../../../../shared/ui/data-table/column-def';
import { DataTable } from '../../../../shared/ui/data-table/data-table';
import { LoadState } from '../../../../shared/ui/load-state/load-state';
import { NotFoundPanel } from '../../../../shared/ui/not-found-panel/not-found-panel';
import { SectionCard } from '../../../../shared/ui/section-card/section-card';
import { ReturnCard } from '../../components/return-card/return-card';
import { Refund } from '../../models/return.model';
import { ReturnDetailStore } from '../../state/return-detail.store';
import { AccessLink } from '../../../../shared/ui/access-link/access-link';

/** One return request (GET /api/returns/{id}): the request with both notes, who it involves, and the order's refunds. */
@Component({
  selector: 'app-return-detail',
  imports: [
    RouterLink,
    Breadcrumbs,
    DataTable,
    LoadState,
    NotFoundPanel,
    SectionCard,
    ReturnCard,
    AccessLink,
  ],
  providers: [ReturnDetailStore],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @if (store.notFound()) {
      <app-not-found-panel what="Return" backLink="/returns" />
    } @else {
      <app-load-state
        [loading]="store.loading()"
        [error]="store.error()"
        [hasValue]="!!store.item()"
        (retry)="store.reload()"
      >
        @if (store.item(); as item) {
          <app-breadcrumbs [items]="crumbs()" />
          <h1 class="h4 mb-3">Return #{{ item.id }}</h1>

          <div class="row g-4">
            <div class="col-xl-8 d-flex flex-column gap-4">
              <app-section-card title="Request">
                <app-return-card [item]="item" />
              </app-section-card>

              <app-section-card title="Refunds on this order" [flush]="true">
                <app-data-table
                  caption="Refunds on the same order"
                  emptyText="No refunds on this order."
                  [rows]="store.refunds()"
                  [columns]="refundColumns"
                  [rowId]="refundId"
                />
              </app-section-card>
            </div>

            <div class="col-xl-4">
              <app-section-card title="Involved">
                <dl class="kv-list">
                  <dt>Order</dt>
                  <dd>
                    <a class="font-monospace" [routerLink]="['/orders', item.order.orderNumber]">
                      {{ item.order.orderNumber }}
                    </a>
                  </dd>
                  <dt>Customer</dt>
                  <dd>
                    <app-access-link [to]="['/customers', item.customer.id]">{{
                      item.customer.name
                    }}</app-access-link>
                  </dd>
                  <dt>Product</dt>
                  <dd>
                    <a [routerLink]="['/products', item.product.id]">{{ item.product.name }}</a>
                  </dd>
                  <dt>Quantity</dt>
                  <dd class="num">{{ item.quantity }}</dd>
                </dl>
              </app-section-card>
            </div>
          </div>
        }
      </app-load-state>
    }
  `,
})
export class ReturnDetail {
  protected readonly store = inject(ReturnDetailStore);

  protected readonly crumbs = computed<Crumb[]>(() => [
    { label: 'Returns', link: '/returns' },
    { label: `#${this.store.item()?.id ?? ''}` },
  ]);

  protected readonly refundId = (r: Refund) => r.id;
  protected readonly refundColumns: ColumnDef<Refund>[] = [
    { header: 'Created', value: (r) => r.createdAt, type: 'datetime' },
    {
      header: 'Reason',
      value: (r) => r.reason,
      hint: (r) =>
        r.returnRequestId === this.store.item()?.id
          ? 'From this return'
          : r.returnRequestId
            ? `From return #${r.returnRequestId}`
            : null,
    },
    { header: 'Issued by', value: (r) => r.issuedBy?.name ?? null },
    { header: 'Status', value: (r) => r.status, type: 'status' },
    { header: 'Amount', value: (r) => r.amount, type: 'money' },
  ];
}
