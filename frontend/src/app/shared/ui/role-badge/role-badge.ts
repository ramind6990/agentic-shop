import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';

/** Display names for backend Role values. */
const ROLE_LABELS: Record<string, string> = {
  CUSTOMER: 'Customer',
  SELLER: 'Seller',
  SUPPORT_T1: 'Support Tier 1',
  SUPPORT_T2: 'Support Tier 2',
  FINANCE: 'Finance',
  FRAUD: 'Fraud team',
  ADMIN: 'Admin',
};

/** Roles that see restricted data get a warmer colour, so access levels stand out. */
const RESTRICTED = new Set(['SUPPORT_T2', 'FINANCE', 'FRAUD', 'ADMIN']);

/** A role name, e.g. a document's minimum role or a staff user's role. */
@Component({
  selector: 'app-role-badge',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <span class="badge fw-medium" [class]="classes()">
      @if (prefix()) {
        <span class="fw-normal">{{ prefix() }}</span>
      }
      {{ label() }}
    </span>
  `,
})
export class RoleBadge {
  readonly role = input.required<string>();
  /** Optional lead-in such as "Min role:". */
  readonly prefix = input<string>();

  protected readonly label = computed(() => ROLE_LABELS[this.role()] ?? this.role());
  protected readonly classes = computed(() =>
    RESTRICTED.has(this.role())
      ? 'bg-danger-subtle text-danger-emphasis border border-danger-subtle'
      : 'bg-primary-subtle text-primary-emphasis border border-primary-subtle',
  );
}
