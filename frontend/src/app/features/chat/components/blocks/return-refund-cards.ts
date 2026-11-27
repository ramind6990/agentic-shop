import { DatePipe, DecimalPipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, input } from '@angular/core';

import { StatusBadge } from '../../../../shared/ui/status-badge/status-badge';
import { EnumLabelPipe } from '../../../../shared/utils/enum-label.pipe';
import { RefundItem, ReturnItem } from '../../models/chat.model';

/** Return requests: what, how many, why, and where each stands. */
@Component({
  selector: 'app-return-cards',
  imports: [DatePipe, StatusBadge, EnumLabelPipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <ul class="block-grid" style="--block-min: 17rem">
      @for (r of items(); track r.id) {
        <li class="block-card">
          <div class="block-head">
            <span>{{ r.quantity }} × {{ r.product }}</span>
            <app-status-badge [status]="r.status" />
          </div>
          <div class="block-meta">Order {{ r.orderNumber }} · {{ r.reason | enumLabel }}</div>
          @if (r.resolutionNote) {
            <div class="line-clamp" [title]="r.resolutionNote">{{ r.resolutionNote }}</div>
          }
          <div class="block-foot">
            <span>Opened {{ r.openedAt | date: 'MMM d, y' }}</span>
            @if (r.resolvedAt) {
              <span>Resolved {{ r.resolvedAt | date: 'MMM d' }}</span>
            }
          </div>
        </li>
      }
    </ul>
  `,
})
export class ReturnCards {
  readonly items = input.required<ReturnItem[]>();
}

/** Refunds (amounts in the order's currency). */
@Component({
  selector: 'app-refund-cards',
  imports: [DatePipe, DecimalPipe, StatusBadge],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <ul class="block-grid" style="--block-min: 15rem">
      @for (r of items(); track r.id) {
        <li class="block-card">
          <div class="block-head">
            <span class="num amount">{{ r.amount | number: '1.2-2' }}</span>
            <app-status-badge [status]="r.status" />
          </div>
          <div class="block-meta">
            Order {{ r.orderNumber }}{{ r.returnId ? ' · for a return' : '' }}
          </div>
          @if (r.reason) {
            <div class="line-clamp" [title]="r.reason">{{ r.reason }}</div>
          }
          <div class="block-foot">
            <span>Created {{ r.createdAt | date: 'MMM d, y' }}</span>
          </div>
        </li>
      }
    </ul>
  `,
  styles: `
    .amount {
      font-size: 0.9375rem;
    }
  `,
})
export class RefundCards {
  readonly items = input.required<RefundItem[]>();
}
