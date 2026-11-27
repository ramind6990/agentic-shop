import { DatePipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, input } from '@angular/core';

import { StarRating } from '../../../../shared/ui/star-rating/star-rating';
import { Review } from '../../models/chat.model';

/** Shoppers' reviews of one product. */
@Component({
  selector: 'app-review-cards',
  imports: [DatePipe, StarRating],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <ul class="block-grid" style="--block-min: 20rem">
      @for (r of items(); track r.id) {
        <li class="block-card">
          <app-star-rating [value]="r.rating" [showValue]="false" />
          @if (r.title) {
            <div class="fw-semibold">{{ r.title }}</div>
          }
          @if (r.body) {
            <p class="mb-0 body line-clamp" style="--lines: 5" [title]="r.body">{{ r.body }}</p>
          }
          <div class="block-foot">
            <span>
              {{ r.authorName }} · {{ r.createdAt | date: 'MMM d, y' }}
              @if (r.verifiedPurchase) {
                · <span class="text-success-emphasis">Verified</span>
              }
            </span>
            @if (r.helpfulCount > 0) {
              <span>{{ r.helpfulCount }} found it helpful</span>
            }
          </div>
        </li>
      }
    </ul>
  `,
  styles: `
    .body {
      white-space: pre-line;
    }
  `,
})
export class ReviewCards {
  readonly items = input.required<Review[]>();
}
