import { DatePipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, input } from '@angular/core';
import { RouterLink } from '@angular/router';

import { StatusBadge } from '../../../../shared/ui/status-badge/status-badge';
import { enumLabel } from '../../../../shared/utils/enum-label';
import { ReturnRequest } from '../../models/return.model';

/** One return request with the customer's and seller's notes. Reused by order detail and the returns screens. */
@Component({
  selector: 'app-return-card',
  imports: [DatePipe, RouterLink, StatusBadge],
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: { class: 'd-block' },
  template: `
    <article class="small">
      <div class="d-flex flex-wrap align-items-center gap-2 mb-1">
        <a [routerLink]="['/returns', item().id]" class="fw-semibold">Return #{{ item().id }}</a>
        <app-status-badge [status]="item().status" />
        <span class="badge text-bg-light border">{{ label(item().reason) }}</span>
        <span class="ms-auto text-body-secondary">
          Opened <time [attr.datetime]="item().openedAt">{{ item().openedAt | date: 'mediumDate' }}</time>
          @if (item().resolvedAt; as resolved) {
            · resolved <time [attr.datetime]="resolved">{{ resolved | date: 'mediumDate' }}</time>
          }
        </span>
      </div>
      <div class="mb-2">{{ item().quantity }} × {{ item().product.name }}</div>
      @if (item().customerNote) {
        <blockquote class="note mb-2">
          <div class="section-label">Customer</div>
          {{ item().customerNote }}
        </blockquote>
      }
      @if (item().sellerNote) {
        <blockquote class="note mb-0">
          <div class="section-label">Seller</div>
          {{ item().sellerNote }}
        </blockquote>
      }
    </article>
  `,
  styles: `
    .note {
      padding: 0.5rem 0.75rem;
      border-left: 3px solid var(--bs-border-color);
      background: var(--bs-tertiary-bg);
    }
  `,
})
export class ReturnCard {
  /** Named `item` because `return` is a reserved word in templates. */
  readonly item = input.required<ReturnRequest>();

  protected readonly label = enumLabel;
}
