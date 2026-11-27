import { DatePipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, input } from '@angular/core';

import { StatusBadge } from '../../../../shared/ui/status-badge/status-badge';
import { EnumLabelPipe } from '../../../../shared/utils/enum-label.pipe';
import { TicketDetail, TicketItem } from '../../models/chat.model';

/** The header line of a ticket: number, subject, status and the facts that tell tickets apart. */
@Component({
  selector: 'app-ticket-summary',
  imports: [DatePipe, StatusBadge, EnumLabelPipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @let t = ticket();
    <div class="block-head">
      <span class="num">{{ t.ticketNumber }}</span>
      <span class="d-flex flex-wrap justify-content-end gap-1">
        @if (t.priority !== 'NORMAL' && t.priority !== 'LOW') {
          <app-status-badge [status]="t.priority" />
        }
        <app-status-badge [status]="t.status" />
      </span>
    </div>
    <div class="subject">{{ t.subject }}</div>
    <div class="block-meta">
      {{ t.category | enumLabel }} · {{ t.tier
      }}{{ t.orderNumber ? ' · order ' + t.orderNumber : ''
      }}{{ t.customer ? ' · ' + t.customer : '' }}{{ t.assignee ? ' · with ' + t.assignee : '' }}
      · updated {{ t.updatedAt | date: 'MMM d, HH:mm' }}
    </div>
  `,
  host: { class: 'd-flex flex-column gap-1 small' },
  styles: `
    .subject {
      font-size: 0.875rem;
    }
  `,
})
export class TicketSummary {
  readonly ticket = input.required<TicketItem>();
}

/** Support tickets (a list, or the one an action just changed). */
@Component({
  selector: 'app-ticket-cards',
  imports: [TicketSummary],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <ul class="block-grid" style="--block-min: 20rem">
      @for (t of items(); track t.ticketNumber) {
        <li class="block-card"><app-ticket-summary [ticket]="t" /></li>
      }
    </ul>
  `,
})
export class TicketCards {
  readonly items = input.required<TicketItem[]>();
}

/** One ticket with its latest messages, oldest first; staff-only notes are marked. */
@Component({
  selector: 'app-ticket-thread',
  imports: [DatePipe, TicketSummary, EnumLabelPipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @let d = ticket();
    <article class="card">
      <div class="card-body p-3 d-flex flex-column gap-2">
        <app-ticket-summary [ticket]="d.ticket" />
        @if (d.messageCount > d.messages.length) {
          <div class="small text-body-secondary">
            {{ d.messageCount - d.messages.length }} earlier messages not shown
          </div>
        }
        <ol class="list-unstyled mb-0 d-flex flex-column gap-2">
          @for (m of d.messages; track $index) {
            <li class="message small" [class.internal]="m.internal">
              <div class="d-flex flex-wrap gap-2 text-body-secondary">
                <span class="fw-semibold text-body">{{ m.authorName }}</span>
                <span>{{ m.author | enumLabel }}</span>
                @if (m.internal) {
                  <span class="badge text-bg-warning">Internal note</span>
                }
                <span class="ms-auto">{{ m.createdAt | date: 'MMM d, HH:mm' }}</span>
              </div>
              <div class="body">{{ m.body }}</div>
            </li>
          }
        </ol>
      </div>
    </article>
  `,
  styles: `
    .message {
      padding: 0.4rem 0.6rem;
      border-radius: 0.5rem;
      background: var(--bs-tertiary-bg);

      &.internal {
        background: var(--bs-warning-bg-subtle);
      }
    }

    .body {
      white-space: pre-line;
    }
  `,
})
export class TicketThread {
  readonly ticket = input.required<TicketDetail>();
}
