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
    <ul class="d-flex flex-column gap-2 list-unstyled mb-0">
      @for (r of items(); track r.id) {
        <li class="card">
          <div class="card-body p-2 px-3">
            <div class="d-flex flex-wrap align-items-center gap-2">
              <app-star-rating [value]="r.rating" [showValue]="false" />
              @if (r.title) {
                <span class="fw-semibold">{{ r.title }}</span>
              }
            </div>
            @if (r.body) {
              <p class="small mb-1 mt-1 body">{{ r.body }}</p>
            }
            <div class="small text-body-secondary">
              {{ r.authorName }} · {{ r.createdAt | date: 'MMM d, y' }}
              @if (r.verifiedPurchase) {
                · <span class="text-success-emphasis">Verified purchase</span>
              }
              @if (r.helpfulCount > 0) {
                · {{ r.helpfulCount }} found it helpful
              }
            </div>
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
