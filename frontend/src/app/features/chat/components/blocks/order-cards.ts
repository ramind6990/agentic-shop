import { DatePipe, NgOptimizedImage } from '@angular/common';
import { ChangeDetectionStrategy, Component, input } from '@angular/core';

import { Price } from '../../../../shared/ui/price/price';
import { StatusBadge } from '../../../../shared/ui/status-badge/status-badge';
import { OrderItem, OrderLine } from '../../models/chat.model';

/** How many line thumbnails a card shows before "+N". */
const THUMBS = 4;

/** The orders of a reply, as compact cards (two or more per row). */
@Component({
  selector: 'app-order-cards',
  imports: [DatePipe, NgOptimizedImage, Price, StatusBadge],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <ul class="block-grid" style="--block-min: 17rem">
      @for (o of items(); track o.orderNumber) {
        <li class="block-card">
          <div class="block-head">
            <span class="num">{{ o.orderNumber }}</span>
            <app-status-badge [status]="o.status" />
          </div>
          <div class="block-meta">
            Placed {{ o.placedAt | date: 'MMM d, y' }} · {{ o.shippingCity
            }}{{ o.seller ? ' · ' + o.seller : '' }}
          </div>

          <div class="lines">
            @let thumbs = shown(o.lines);
            <div class="thumbs" aria-hidden="true">
              @for (l of thumbs; track $index) {
                <span class="thumb" [title]="l.title">
                  <img [ngSrc]="l.thumbnailUrl" width="72" height="72" alt="" (error)="hide($event)" />
                  @if (l.quantity > 1) {
                    <span class="qty">×{{ l.quantity }}</span>
                  }
                </span>
              }
              @if (thumbs.length < o.lines.length) {
                <span class="thumb more">+{{ o.lines.length - thumbs.length }}</span>
              }
            </div>
            <div class="names line-clamp" style="--lines: 2">
              @for (l of o.lines; track $index; let last = $last) {
                {{ l.title }} × {{ l.quantity }}{{ last ? '' : ', ' }}
              }
            </div>
          </div>

          <div class="block-foot">
            <span>
              @if (o.estimatedDelivery) {
                Arrives by {{ o.estimatedDelivery | date: 'MMM d' }}
              } @else if (o.trackingNumber) {
                Tracking {{ o.trackingNumber }}
              } @else {
                {{ o.lines.length }} {{ o.lines.length === 1 ? 'item' : 'items' }}
              }
            </span>
            <app-price class="total text-body" [price]="o.total" [currency]="o.currency" />
          </div>
        </li>
      }
    </ul>
  `,
  styles: `
    .lines {
      display: flex;
      flex-direction: column;
      gap: 0.35rem;
      margin: 0.2rem 0 0.1rem;
    }

    .thumbs {
      display: flex;
      gap: 0.35rem;
    }

    .thumb {
      position: relative;
      width: 2.5rem;
      height: 2.5rem;
      flex-shrink: 0;
      overflow: hidden;
      border-radius: 0.5rem;
      background: var(--bs-secondary-bg);

      img {
        width: 100%;
        height: 100%;
        object-fit: cover;
      }

      &.more {
        display: inline-flex;
        align-items: center;
        justify-content: center;
        font-size: 0.75rem;
        font-weight: 600;
        color: var(--bs-secondary-color);
      }
    }

    .qty {
      position: absolute;
      right: 0.1rem;
      bottom: 0.1rem;
      padding: 0 0.2rem;
      font-size: 0.625rem;
      font-weight: 600;
      line-height: 1.3;
      color: #fff;
      background: rgb(0 0 0 / 0.6);
      border-radius: 0.25rem;
    }

    .names {
      color: var(--bs-body-color);
    }

    .total {
      font-size: 0.9375rem;
    }
  `,
})
export class OrderCards {
  readonly items = input.required<OrderItem[]>();

  /** The lines that get a thumbnail: all of them when they fit, else one slot less to make room for "+N". */
  protected shown(lines: OrderLine[]): OrderLine[] {
    return lines.length > THUMBS ? lines.slice(0, THUMBS - 1) : lines;
  }

  /** Mock thumbnails may not exist: keep the grey placeholder instead of a broken image. */
  protected hide(event: Event): void {
    (event.target as HTMLElement).style.visibility = 'hidden';
  }
}
