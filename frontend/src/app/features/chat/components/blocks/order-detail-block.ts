import { CurrencyPipe, DatePipe, NgOptimizedImage } from '@angular/common';
import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';

import { AddressBlock } from '../../../../shared/ui/address-block/address-block';
import { StatusBadge } from '../../../../shared/ui/status-badge/status-badge';
import { EnumLabelPipe } from '../../../../shared/utils/enum-label.pipe';
import { OrderDetail, Verdict } from '../../models/chat.model';

/**
 * One order in full: its lines, totals, delivery address, shipments, payments, returns and refunds, and what can be
 * done with it now (with the reason when an action is not possible).
 */
@Component({
  selector: 'app-order-detail-block',
  imports: [CurrencyPipe, DatePipe, NgOptimizedImage, AddressBlock, StatusBadge, EnumLabelPipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @let o = order();
    @let c = o.card;
    <article class="card">
      <div class="card-body p-3 d-flex flex-column gap-3">
        <div class="d-flex flex-wrap align-items-center gap-2">
          <span class="fw-semibold">{{ c.orderNumber }}</span>
          <app-status-badge [status]="c.status" />
          <span class="small text-body-secondary">placed {{ c.placedAt | date: 'MMM d, y' }}</span>
          @if (c.seller) {
            <span class="small text-body-secondary">· {{ c.seller }}</span>
          }
          <span class="ms-auto fw-semibold num">{{ c.total | currency: c.currency }}</span>
        </div>

        <ul class="list-unstyled mb-0 d-flex flex-column gap-2">
          @for (l of c.lines; track l.productId) {
            <li class="d-flex align-items-center gap-2">
              <span class="thumb rounded">
                <img
                  [ngSrc]="l.thumbnailUrl"
                  width="48"
                  height="48"
                  alt=""
                  (error)="hide($event)"
                />
              </span>
              <span class="flex-grow-1 small">{{ l.title }}</span>
              <span class="small text-body-secondary text-nowrap num">
                {{ l.quantity }} × {{ l.unitPrice | currency: c.currency }}
              </span>
            </li>
          }
        </ul>

        <div class="small d-flex flex-wrap gap-3 text-body-secondary num">
          <span>Subtotal {{ o.subtotal | currency: c.currency }}</span>
          <span>Shipping {{ o.shippingFee | currency: c.currency }}</span>
          <span>Tax {{ o.tax | currency: c.currency }}</span>
        </div>

        <div class="row g-3 small">
          <div class="col-sm-6">
            <div class="section-title">Delivery address</div>
            <app-address-block [address]="o.shippingAddress" />
          </div>
          <div class="col-sm-6">
            <div class="section-title">Shipments</div>
            @for (s of o.shipments; track s.trackingNumber) {
              <div class="d-flex flex-wrap align-items-center gap-1">
                {{ s.carrier }} {{ s.trackingNumber }} <app-status-badge [status]="s.status" />
              </div>
              <div class="text-body-secondary">
                @if (s.deliveredAt) {
                  Delivered {{ s.deliveredAt | date: 'MMM d' }}
                } @else if (s.estimatedDelivery) {
                  Arrives by {{ s.estimatedDelivery | date: 'MMM d' }}
                }
              </div>
            } @empty {
              <div class="text-body-secondary">Not shipped yet</div>
            }
            <div class="section-title mt-2">Payment</div>
            @for (p of o.payments; track $index) {
              <div>
                {{ p.method | enumLabel }} · {{ p.amount | currency: c.currency }}
                <app-status-badge [status]="p.status" />
              </div>
            }
          </div>
        </div>

        @if (o.returns.length || o.refunds.length) {
          <div class="small">
            @for (r of o.returns; track r.id) {
              <div>
                Return of {{ r.quantity }} × {{ lineTitle(r.itemId) }} ({{ r.reason | enumLabel }})
                <app-status-badge [status]="r.status" />
              </div>
            }
            @for (r of o.refunds; track r.id) {
              <div>
                Refund {{ r.amount | currency: c.currency }} {{ r.reason ? '· ' + r.reason : '' }}
                <app-status-badge [status]="r.status" />
              </div>
            }
          </div>
        }

        <div class="d-flex flex-wrap gap-2 small" role="group" aria-label="What can be done now">
          @for (a of actions(); track a.label) {
            <span
              class="badge rounded-pill fw-normal"
              [class]="a.verdict.allowed ? 'text-bg-success-subtle' : 'text-bg-light border'"
              [title]="a.verdict.reason ?? ''"
            >
              {{ a.verdict.allowed ? '✓' : '✕' }} {{ a.label }}
            </span>
          }
          @if (o.actions.returnDeadline && o.actions.returnItems.allowed) {
            <span class="text-body-secondary"
              >returns until {{ o.actions.returnDeadline | date: 'MMM d, y' }}</span
            >
          }
        </div>
      </div>
    </article>
  `,
  styles: `
    .thumb {
      flex: 0 0 auto;
      width: 3rem;
      height: 3rem;
      overflow: hidden;
      background: var(--bs-secondary-bg);

      img {
        width: 100%;
        height: 100%;
        object-fit: cover;
      }
    }

    .section-title {
      font-weight: 600;
      margin-bottom: 0.15rem;
    }

    .text-bg-success-subtle {
      background: var(--bs-success-bg-subtle);
      color: var(--bs-success-text-emphasis);
    }
  `,
})
export class OrderDetailBlock {
  readonly order = input.required<OrderDetail>();

  protected readonly actions = computed<{ label: string; verdict: Verdict }[]>(() => {
    const a = this.order().actions;
    return [
      { label: 'Cancel', verdict: a.cancel },
      { label: 'Change address', verdict: a.changeAddress },
      { label: 'Return items', verdict: a.returnItems },
    ];
  });

  protected lineTitle(itemId: number): string {
    return this.order().card.lines.find((l) => l.itemId === itemId)?.title ?? 'an item';
  }

  protected hide(event: Event): void {
    (event.target as HTMLElement).style.visibility = 'hidden';
  }
}
