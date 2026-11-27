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
    <ul class="d-flex flex-column gap-2 list-unstyled mb-0">
      @for (s of items(); track s.trackingNumber) {
        <li class="card">
          <div class="card-body p-2 px-3 small">
            <div class="d-flex flex-wrap align-items-center gap-2">
              <span class="fw-semibold">{{ s.carrier }} {{ s.trackingNumber }}</span>
              <app-status-badge [status]="s.status" />
              <span class="ms-auto text-body-secondary">
                @if (s.deliveredAt) {
                  Delivered {{ s.deliveredAt | date: 'MMM d' }}
                } @else if (s.estimatedDelivery) {
                  Due {{ s.estimatedDelivery | date: 'MMM d' }}
                }
              </span>
            </div>
            <div class="text-body-secondary">
              Order {{ s.orderNumber }} · {{ s.customer }} · {{ s.seller }}
              @if (s.shippedAt) {
                · shipped {{ s.shippedAt | date: 'MMM d' }}
              }
            </div>
          </div>
        </li>
      }
    </ul>
  `,
})
export class ShipmentRows {
  readonly items = input.required<ShipmentRow[]>();
}
