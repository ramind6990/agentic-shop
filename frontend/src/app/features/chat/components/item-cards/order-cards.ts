import { DatePipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, input } from '@angular/core';

import { Price } from '../../../../shared/ui/price/price';
import { StatusBadge } from '../../../../shared/ui/status-badge/status-badge';
import { OrderItem } from '../../models/chat.model';

/** The orders of a reply, as compact cards. */
@Component({
  selector: 'app-order-cards',
  imports: [DatePipe, Price, StatusBadge],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <ul class="d-flex flex-column gap-2 list-unstyled mb-0">
      @for (o of items(); track o.orderNumber) {
        <li class="card">
          <div class="card-body p-2 px-3">
            <div class="d-flex flex-wrap align-items-center gap-2">
              <span class="fw-semibold">{{ o.orderNumber }}</span>
              <app-status-badge [status]="o.status" />
              <span class="small text-body-secondary">placed {{ o.placedAt | date: 'MMM d, y' }}</span>
              <app-price class="ms-auto" [price]="o.total" [currency]="o.currency" />
            </div>
            <div class="small mt-1">
              @for (l of o.lines; track l.productId; let last = $last) {
                {{ l.title }} × {{ l.quantity }}{{ last ? '' : ', ' }}
              }
            </div>
            @if (o.estimatedDelivery || o.trackingNumber) {
              <div class="small text-body-secondary mt-1">
                @if (o.estimatedDelivery) {
                  Arrives by {{ o.estimatedDelivery | date: 'MMM d' }}
                }
                @if (o.trackingNumber) {
                  {{ o.estimatedDelivery ? '·' : '' }} tracking {{ o.trackingNumber }}
                }
              </div>
            }
          </div>
        </li>
      }
    </ul>
  `,
})
export class OrderCards {
  readonly items = input.required<OrderItem[]>();
}
