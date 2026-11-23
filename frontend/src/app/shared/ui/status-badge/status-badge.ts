import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';

import { enumLabel } from '../../utils/enum-label';

type Tone = 'success' | 'warning' | 'danger' | 'info' | 'secondary';

/**
 * Colour for any backend status enum value. Shared so orders, tickets, returns, sellers and
 * products all show the same colour for the same meaning. Unknown values fall back to grey.
 */
const TONES: Record<string, Tone> = {
  ACTIVE: 'success',
  DELIVERED: 'success',
  PAID: 'success',
  CAPTURED: 'success',
  ISSUED: 'success',
  AUTHORIZED: 'info',
  RECEIVED: 'info',
  LABEL_CREATED: 'secondary',
  VOIDED: 'secondary',
  FAILED: 'danger',
  ESCALATED: 'danger',
  // ticket priorities
  URGENT: 'danger',
  HIGH: 'warning',
  NORMAL: 'secondary',
  LOW: 'secondary',
  APPROVED: 'success',
  RESOLVED: 'success',
  CLOSED: 'secondary',
  DRAFT: 'secondary',
  PENDING: 'warning',
  REQUESTED: 'warning',
  IN_TRANSIT: 'info',
  SHIPPED: 'info',
  OPEN: 'info',
  ON_HOLD: 'warning',
  PARTIALLY_REFUNDED: 'warning',
  SUSPENDED: 'danger',
  DELISTED: 'danger',
  CANCELLED: 'danger',
  REJECTED: 'danger',
  DISPUTED: 'danger',
  LOST: 'danger',
  REFUNDED: 'secondary',
  RETURNED: 'secondary',
  OFFBOARDED: 'secondary',
};

@Component({
  selector: 'app-status-badge',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `<span class="badge fw-medium" [class]="classes()">{{ label() }}</span>`,
})
export class StatusBadge {
  /** Raw enum value, e.g. PARTIALLY_REFUNDED. */
  readonly status = input.required<string>();

  protected readonly label = computed(() => enumLabel(this.status()));

  protected readonly classes = computed(() => {
    const tone = TONES[this.status()] ?? 'secondary';
    return `bg-${tone}-subtle text-${tone}-emphasis border border-${tone}-subtle`;
  });
}
