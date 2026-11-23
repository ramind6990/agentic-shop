import { ChangeDetectionStrategy, Component, input } from '@angular/core';

/**
 * Titled card used for blocks on detail pages (attributes, reviews, payments, shipments…).
 * Content with `slot="actions"` goes on the right of the header.
 */
@Component({
  selector: 'app-section-card',
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: { class: 'd-block' },
  template: `
    <section class="card" [attr.aria-labelledby]="headingId">
      <div class="card-header d-flex flex-wrap align-items-center gap-2 py-2">
        <h2 class="h6 mb-0 me-auto" [id]="headingId">{{ title() }}</h2>
        <ng-content select="[slot=actions]" />
      </div>
      <div class="card-body" [class.p-0]="flush()">
        <ng-content />
      </div>
    </section>
  `,
})
export class SectionCard {
  private static nextId = 0;

  readonly title = input.required<string>();
  /** Remove body padding, for edge-to-edge tables and lists. */
  readonly flush = input(false);

  protected readonly headingId = `section-card-${SectionCard.nextId++}`;
}
