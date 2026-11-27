import { DatePipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, input } from '@angular/core';

import { StatusBadge } from '../../../../shared/ui/status-badge/status-badge';
import { ShipmentRow } from '../../models/chat.model';

/** Shipments across customers (staff search). */
@Component({
  selector: 'app-shipment-rows',
  imports: [DatePipe, StatusBadge],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <ul class="block-grid" style="--block-min: 17rem">
      @for (s of items(); track s.trackingNumber) {
        <li class="block-card">
          <div class="block-head">
            <span>{{ s.carrier }} <span class="num">{{ s.trackingNumber }}</span></span>
            <app-status-badge [status]="s.status" />
          </div>
          <div class="block-meta">Order {{ s.orderNumber }} · {{ s.customer }} · {{ s.seller }}</div>
          <div class="block-foot">
            <span>
              @if (s.shippedAt) {
                Shipped {{ s.shippedAt | date: 'MMM d' }}
              }
            </span>
            <span>
              @if (s.deliveredAt) {
                Delivered {{ s.deliveredAt | date: 'MMM d' }}
              } @else if (s.estimatedDelivery) {
                Due {{ s.estimatedDelivery | date: 'MMM d' }}
              }
            </span>
          </div>
        </li>
      }
    </ul>
  `,
})
export class ShipmentRows {
  readonly items = input.required<ShipmentRow[]>();
}
