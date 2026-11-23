import { DatePipe, DecimalPipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, input } from '@angular/core';

import { StarRating } from '../../../../shared/ui/star-rating/star-rating';
import { Review } from '../../models/product.model';

@Component({
  selector: 'app-review-card',
  imports: [DatePipe, DecimalPipe, StarRating],
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: { class: 'd-block' },
  template: `
    <article class="py-3">
      <div class="d-flex flex-wrap align-items-center gap-2 mb-1">
        <app-star-rating [value]="review().rating" />
        <h3 class="h6 mb-0">{{ review().title }}</h3>
      </div>
      <p class="mb-2">{{ review().body }}</p>
      <div class="d-flex flex-wrap gap-3 small text-body-secondary">
        <span>{{ review().authorName }}</span>
        <time [attr.datetime]="review().createdAt">{{ review().createdAt | date: 'mediumDate' }}</time>
        @if (review().verifiedPurchase) {
          <span class="text-success-emphasis">Verified purchase</span>
        }
        <span class="num">{{ review().helpfulCount | number }} found this helpful</span>
      </div>
    </article>
  `,
})
export class ReviewCard {
  readonly review = input.required<Review>();
}
