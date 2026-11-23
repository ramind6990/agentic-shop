import { DatePipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, input } from '@angular/core';

import { StatusBadge } from '../../../../shared/ui/status-badge/status-badge';
import { Shipment } from '../../models/order.model';

/** Parcels for the order: carrier, tracking number, status and shipped/delivered dates. */
@Component({
  selector: 'app-shipment-list',
  imports: [DatePipe, StatusBadge],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <ul class="list-group list-group-flush small">
      @for (shipment of shipments(); track shipment.id) {
        <li class="list-group-item px-3">
          <div class="d-flex justify-content-between align-items-center gap-2">
            <span class="fw-semibold">{{ shipment.carrier }}</span>
            <app-status-badge [status]="shipment.status" />
          </div>
          <div class="font-monospace my-1">{{ shipment.trackingNumber }}</div>
          <div class="text-body-secondary">
            Shipped
            @if (shipment.shippedAt) {
              <time [attr.datetime]="shipment.shippedAt">{{ shipment.shippedAt | date: 'MMM d' }}</time>
            } @else {
              –
            }
            · Delivered
            @if (shipment.deliveredAt) {
              <time [attr.datetime]="shipment.deliveredAt">{{ shipment.deliveredAt | date: 'MMM d' }}</time>
            } @else {
              –
            }
          </div>
        </li>
      } @empty {
        <li class="list-group-item px-3 text-body-secondary">Not shipped yet.</li>
      }
    </ul>
  `,
})
export class ShipmentList {
  readonly shipments = input.required<Shipment[]>();
}
