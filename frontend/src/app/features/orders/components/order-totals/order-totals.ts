import { CurrencyPipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';

import { OrderDetail } from '../../models/order.model';

/** Money breakdown: subtotal, shipping, tax, total, then refunds and what the customer paid net. */
@Component({
  selector: 'app-order-totals',
  imports: [CurrencyPipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <dl class="totals mb-0 num">
      <dt>Subtotal</dt>
      <dd>{{ order().subtotal | currency: order().currency }}</dd>
      <dt>Shipping</dt>
      <dd>
        @if (order().shippingFee === 0) {
          Free
        } @else {
          {{ order().shippingFee | currency: order().currency }}
        }
      </dd>
      <dt>Tax</dt>
      <dd>{{ order().tax | currency: order().currency }}</dd>
      <dt class="total">Total</dt>
      <dd class="total">{{ order().total | currency: order().currency }}</dd>
      @if (refunded() > 0) {
        <dt class="text-danger-emphasis">Refunded</dt>
        <dd class="text-danger-emphasis">−{{ refunded() | currency: order().currency }}</dd>
        <dt class="fw-semibold">Net paid</dt>
        <dd class="fw-semibold">{{ order().total - refunded() | currency: order().currency }}</dd>
      }
    </dl>
  `,
  styleUrl: './order-totals.scss',
})
export class OrderTotals {
  readonly order = input.required<OrderDetail>();

  /** Only issued refunds count; pending or failed ones have not moved money. */
  protected readonly refunded = computed(() =>
    this.order()
      .refunds.filter((r) => r.status === 'ISSUED')
      .reduce((sum, r) => sum + r.amount, 0),
  );
}
