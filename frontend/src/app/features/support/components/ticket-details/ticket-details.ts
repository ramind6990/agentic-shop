import { DatePipe, DecimalPipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, input } from '@angular/core';
import { RouterLink } from '@angular/router';

import { StatusBadge } from '../../../../shared/ui/status-badge/status-badge';
import { enumLabel } from '../../../../shared/utils/enum-label';
import { TicketDetail } from '../../models/ticket.model';

/** Ticket facts for the side panel: state, routing and the people and order involved. */
@Component({
  selector: 'app-ticket-details',
  imports: [DatePipe, DecimalPipe, RouterLink, StatusBadge],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @let t = ticket();
    <dl class="kv-list">
      <dt>Status</dt>
      <dd><app-status-badge [status]="t.status" /></dd>
      <dt>Priority</dt>
      <dd><app-status-badge [status]="t.priority" /></dd>
      <dt>Tier</dt>
      <dd>{{ t.tier === 'T2' ? 'Tier 2' : 'Tier 1' }}</dd>
      <dt>Category</dt>
      <dd>{{ label(t.category) }}</dd>
      <dt>Assignee</dt>
      <dd>{{ t.assignee?.name ?? 'Unassigned' }}</dd>
      <dt>Customer</dt>
      <dd><a [routerLink]="['/customers', t.customer.id]">{{ t.customer.name }}</a></dd>
      <dt>Order</dt>
      <dd>
        @if (t.order; as order) {
          <a class="font-monospace" [routerLink]="['/orders', order.orderNumber]">{{ order.orderNumber }}</a>
        } @else {
          –
        }
      </dd>
      <dt>Seller</dt>
      <dd>
        @if (t.seller; as seller) {
          <a [routerLink]="['/sellers', seller.id]">{{ seller.name }}</a>
        } @else {
          –
        }
      </dd>
      <dt>Messages</dt>
      <dd class="num">{{ t.messageCount | number }}</dd>
      <dt>Created</dt>
      <dd><time [attr.datetime]="t.createdAt">{{ t.createdAt | date: 'MMM d, y, HH:mm' }}</time></dd>
      <dt>Updated</dt>
      <dd><time [attr.datetime]="t.updatedAt">{{ t.updatedAt | date: 'MMM d, y, HH:mm' }}</time></dd>
    </dl>
  `,
})
export class TicketDetails {
  readonly ticket = input.required<TicketDetail>();

  protected readonly label = enumLabel;
}
