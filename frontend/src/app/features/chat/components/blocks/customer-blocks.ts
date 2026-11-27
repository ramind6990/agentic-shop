import { DatePipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, input } from '@angular/core';

import { AddressBlock } from '../../../../shared/ui/address-block/address-block';
import { StatusBadge } from '../../../../shared/ui/status-badge/status-badge';
import { ChatAddress, CustomerDetail, CustomerRow } from '../../models/chat.model';

/** Saved addresses, default first. */
@Component({
  selector: 'app-address-cards',
  imports: [AddressBlock],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <ul class="block-grid" style="--block-min: 14rem">
      @for (a of items(); track $index) {
        <li class="block-card">
          @if (a.label || a.defaultAddress) {
            <div class="block-head">
              <span>{{ a.label }}</span>
              @if (a.defaultAddress) {
                <span class="badge text-bg-light border">Default</span>
              }
            </div>
          }
          <app-address-block [address]="a" />
        </li>
      }
    </ul>
  `,
})
export class AddressCards {
  readonly items = input.required<ChatAddress[]>();
}

/** Customers found by name or email (staff). */
@Component({
  selector: 'app-customer-cards',
  imports: [DatePipe, StatusBadge],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <ul class="block-grid" style="--block-min: 16rem">
      @for (c of items(); track c.id) {
        <li class="block-card">
          <div class="block-head">
            <span>{{ c.fullName }}</span>
            <app-status-badge [status]="c.status" />
          </div>
          <div class="block-meta text-truncate" [title]="c.email">{{ c.email }}</div>
          <div class="block-meta">Customer since {{ c.createdAt | date: 'MMM y' }}</div>
        </li>
      }
    </ul>
  `,
})
export class CustomerCards {
  readonly items = input.required<CustomerRow[]>();
}

/** One customer's profile, history counts and saved addresses (staff). */
@Component({
  selector: 'app-customer-detail-block',
  imports: [DatePipe, StatusBadge, AddressCards],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @let c = customer();
    <article class="card">
      <div class="card-body p-3 d-flex flex-column gap-2">
        <div class="d-flex flex-wrap align-items-center gap-2">
          <span class="fw-semibold">{{ c.fullName }}</span>
          <app-status-badge [status]="c.status" />
          <span class="small text-body-secondary ms-auto"
            >customer since {{ c.createdAt | date: 'MMM d, y' }}</span
          >
        </div>
        <div class="small text-body-secondary">
          {{ c.email }}{{ c.phone ? ' · ' + c.phone : '' }}
        </div>
        <div class="d-flex gap-3 small">
          <span
            ><strong>{{ c.orderCount }}</strong> orders</span
          >
          <span
            ><strong>{{ c.returnCount }}</strong> returns</span
          >
          <span
            ><strong>{{ c.ticketCount }}</strong> tickets</span
          >
        </div>
        @if (c.addresses.length) {
          <app-address-cards [items]="c.addresses" />
        }
      </div>
    </article>
  `,
})
export class CustomerDetailBlock {
  readonly customer = input.required<CustomerDetail>();
}
