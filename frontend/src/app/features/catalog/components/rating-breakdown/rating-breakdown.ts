import { DecimalPipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';

import { StarRating } from '../../../../shared/ui/star-rating/star-rating';
import { RatingSummary } from '../../models/product.model';

/** Average rating plus one bar per star level (5 to 1). */
@Component({
  selector: 'app-rating-breakdown',
  imports: [DecimalPipe, StarRating],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="d-flex flex-wrap align-items-center gap-4">
      <div class="text-center">
        <div class="display-6 fw-semibold num">{{ summary().average | number: '1.1-1' }}</div>
        <app-star-rating [value]="summary().average" [showValue]="false" />
        <div class="small text-body-secondary num">{{ summary().total | number }} reviews</div>
      </div>
      <ul class="list-unstyled flex-grow-1 mb-0 small">
        @for (row of rows(); track row.stars) {
          <li class="d-flex align-items-center gap-2 mb-1">
            <span class="text-nowrap num" style="width: 2.25rem">{{ row.stars }} ★</span>
            <div
              class="progress flex-grow-1"
              role="progressbar"
              [attr.aria-label]="row.stars + ' star reviews'"
              [attr.aria-valuenow]="row.percent"
              aria-valuemin="0"
              aria-valuemax="100"
            >
              <div class="progress-bar bg-warning" [style.width.%]="row.percent"></div>
            </div>
            <span class="text-body-secondary text-end num" style="width: 2.5rem">{{ row.count | number }}</span>
          </li>
        }
      </ul>
    </div>
  `,
})
export class RatingBreakdown {
  readonly summary = input.required<RatingSummary>();

  protected readonly rows = computed(() => {
    const { distribution, total } = this.summary();
    return [5, 4, 3, 2, 1].map((stars) => {
      const count = distribution[String(stars)] ?? 0;
      return { stars, count, percent: total ? Math.round((count / total) * 100) : 0 };
    });
  });
}
