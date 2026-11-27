import { DatePipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, input } from '@angular/core';

import { StatusBadge } from '../../../../shared/ui/status-badge/status-badge';
import { EnumLabelPipe } from '../../../../shared/utils/enum-label.pipe';
import { Tracking } from '../../models/chat.model';

/** An order's parcels: carrier, status, delivery date and the carrier scans, newest first. */
@Component({
  selector: 'app-tracking-block',
  imports: [DatePipe, StatusBadge, EnumLabelPipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @let t = tracking();
    <div class="d-flex flex-column gap-2">
      @for (s of t.shipments; track s.trackingNumber) {
        <article class="card">
          <div class="card-body p-2 px-3">
            <div class="d-flex flex-wrap align-items-center gap-2">
              <span class="fw-semibold">{{ s.carrier }} {{ s.trackingNumber }}</span>
              <app-status-badge [status]="s.status" />
              <span class="small text-body-secondary ms-auto">
                @if (s.deliveredAt) {
                  Delivered {{ s.deliveredAt | date: 'MMM d, HH:mm' }}
                } @else if (s.estimatedDelivery) {
                  Arrives by {{ s.estimatedDelivery | date: 'EEE, MMM d' }}
                }
              </span>
            </div>
            <div class="small text-body-secondary">Order {{ t.orderNumber }}</div>
            @if (s.events.length) {
              <ol class="timeline list-unstyled small mb-0 mt-2">
                @for (e of reversed(s.events); track $index; let first = $first) {
                  <li [class.current]="first">
                    <span class="fw-medium">{{ e.description || (e.type | enumLabel) }}</span>
                    <span class="text-body-secondary">
                      · {{ e.occurredAt | date: 'MMM d, HH:mm'
                      }}{{ e.location ? ' · ' + e.location : '' }}
                    </span>
                  </li>
                }
              </ol>
              @if (s.eventCount > s.events.length) {
                <div class="small text-body-secondary mt-1">
                  and {{ s.eventCount - s.events.length }} earlier scans
                </div>
              }
            }
          </div>
        </article>
      } @empty {
        <div class="card">
          <div class="card-body p-2 px-3 small">
            Order {{ t.orderNumber }} (<span>{{ t.orderStatus | enumLabel }}</span
            >) has not been handed to a carrier yet.
          </div>
        </div>
      }
    </div>
  `,
  styles: `
    .timeline {
      border-left: 2px solid var(--bs-border-color);
      margin-left: 0.3rem;

      li {
        position: relative;
        padding-left: 0.9rem;
        padding-bottom: 0.35rem;

        &::before {
          content: '';
          position: absolute;
          left: -0.36rem;
          top: 0.4rem;
          width: 0.6rem;
          height: 0.6rem;
          border-radius: 50%;
          background: var(--bs-secondary-bg);
          border: 2px solid var(--bs-border-color);
        }

        &.current::before {
          background: var(--bs-primary);
          border-color: var(--bs-primary);
        }
      }
    }
  `,
})
export class TrackingBlock {
  readonly tracking = input.required<Tracking>();

  protected reversed<T>(items: T[]): T[] {
    return [...items].reverse();
  }
}
