import { CurrencyPipe, DatePipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, input } from '@angular/core';

import { StatusBadge } from '../../../../shared/ui/status-badge/status-badge';
import { enumLabel } from '../../../../shared/utils/enum-label';
import { Payment } from '../../models/order.model';

/** Payment attempts in order; failed attempts are kept as their own rows. */
@Component({
  selector: 'app-payment-list',
  imports: [CurrencyPipe, DatePipe, StatusBadge],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <ul class="list-group list-group-flush small">
      @for (payment of payments(); track payment.id) {
        <li class="list-group-item px-3">
          <div class="d-flex justify-content-between align-items-center gap-2">
            <span class="fw-semibold">{{ label(payment.method) }}</span>
            <span class="num">{{ payment.amount | currency: currency() }}</span>
          </div>
          <div class="d-flex justify-content-between align-items-center gap-2 mt-1">
            <span class="text-body-secondary">
              <time [attr.datetime]="payment.createdAt">{{ payment.createdAt | date: 'MMM d, HH:mm' }}</time>
              · <span class="font-monospace">{{ payment.providerRef }}</span>
            </span>
            <app-status-badge [status]="payment.status" />
          </div>
        </li>
      } @empty {
        <li class="list-group-item px-3 text-body-secondary">No payments.</li>
      }
    </ul>
  `,
})
export class PaymentList {
  readonly payments = input.required<Payment[]>();
  readonly currency = input('USD');

  protected readonly label = enumLabel;
}
