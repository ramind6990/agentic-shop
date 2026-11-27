import { CurrencyPipe, DatePipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, input } from '@angular/core';

import { AddressBlock } from '../../../../shared/ui/address-block/address-block';
import { EnumLabelPipe } from '../../../../shared/utils/enum-label.pipe';
import { ChatAddress, OrderCancellation } from '../../models/chat.model';

/** An order that was just cancelled, and what happens to the money. */
@Component({
  selector: 'app-cancellation-block',
  imports: [CurrencyPipe, DatePipe, EnumLabelPipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @let c = cancellation();
    <div class="card border-success-subtle">
      <div class="card-body p-2 px-3 small">
        <div class="fw-semibold">Order {{ c.orderNumber }} cancelled</div>
        <div class="text-body-secondary">{{ c.cancelledAt | date: 'MMM d, y, HH:mm' }}</div>
        @if (c.refundAmount > 0) {
          <div class="mt-1">
            Refund of <strong>{{ c.refundAmount | currency: c.currency }}</strong>{{
              c.refundMethod ? ' to your ' + (c.refundMethod | enumLabel).toLowerCase() : ''
            }}{{
              c.refundExpectedBy ? ', expected by ' + (c.refundExpectedBy | date: 'MMM d') : ''
            }}
          </div>
        } @else {
          <div class="mt-1">Nothing was charged; the payment was released.</div>
        }
      </div>
    </div>
  `,
})
export class CancellationBlock {
  readonly cancellation = input.required<OrderCancellation>();
}

/** An order's new delivery address. */
@Component({
  selector: 'app-address-change-block',
  imports: [AddressBlock],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="card border-success-subtle">
      <div class="card-body p-2 px-3 small">
        <div class="fw-semibold mb-1">Order {{ orderNumber() }} now goes to</div>
        <app-address-block [address]="address()" />
      </div>
    </div>
  `,
})
export class AddressChangeBlock {
  readonly orderNumber = input.required<string>();
  readonly address = input.required<ChatAddress>();
}
