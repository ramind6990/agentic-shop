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
    <ul class="d-flex flex-column gap-2 list-unstyled mb-0">
      @for (r of items(); track r.id) {
        <li class="card">
          <div class="card-body p-2 px-3 small">
            <div class="d-flex flex-wrap align-items-center gap-2">
              <span class="fw-semibold">{{ r.quantity }} × {{ r.product }}</span>
              <app-status-badge [status]="r.status" />
            </div>
            <div class="text-body-secondary">
              Order {{ r.orderNumber }} · {{ r.reason | enumLabel }} · opened
              {{ r.openedAt | date: 'MMM d, y' }}
              @if (r.resolvedAt) {
                · resolved {{ r.resolvedAt | date: 'MMM d' }}
              }
            </div>
            @if (r.resolutionNote) {
              <div class="mt-1">{{ r.resolutionNote }}</div>
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
    <ul class="d-flex flex-column gap-2 list-unstyled mb-0">
      @for (r of items(); track r.id) {
        <li class="card">
          <div class="card-body p-2 px-3 small">
            <div class="d-flex flex-wrap align-items-center gap-2">
              <span class="fw-semibold num">{{ r.amount | number: '1.2-2' }}</span>
              <app-status-badge [status]="r.status" />
              <span class="text-body-secondary ms-auto">{{ r.createdAt | date: 'MMM d, y' }}</span>
            </div>
            <div class="text-body-secondary">
              Order {{ r.orderNumber }}{{ r.reason ? ' · ' + r.reason : ''
              }}{{ r.returnId ? ' · for a return' : '' }}
            </div>
          </div>
        </li>
      }
    </ul>
  `,
})
export class RefundCards {
  readonly items = input.required<RefundItem[]>();
}
