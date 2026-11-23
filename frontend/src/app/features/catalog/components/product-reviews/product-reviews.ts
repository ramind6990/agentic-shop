import { ChangeDetectionStrategy, Component, input } from '@angular/core';

import { PageResponse } from '../../../../shared/models/api.model';
import { Pager } from '../../../../shared/ui/pager/pager';
import { RatingSummary, Review, ReviewSort } from '../../models/product.model';
import { RatingBreakdown } from '../rating-breakdown/rating-breakdown';
import { ReviewCard } from '../review-card/review-card';

/** Rating breakdown, star filter and sort, then the paged review list. Display-only for now. */
@Component({
  selector: 'app-product-reviews',
  imports: [Pager, RatingBreakdown, ReviewCard],
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: { class: 'd-block' },
  template: `
    <app-rating-breakdown [summary]="summary()" />

    <div class="d-flex flex-wrap align-items-center gap-2 border-top border-bottom py-2 my-3">
      <div class="btn-group btn-group-sm" role="group" aria-label="Filter by rating">
        <button type="button" class="btn btn-outline-secondary active" aria-pressed="true">All</button>
        @for (stars of starFilters; track stars) {
          <button type="button" class="btn btn-outline-secondary" aria-pressed="false">{{ stars }} ★</button>
        }
      </div>
      <div class="ms-auto d-flex align-items-center gap-2">
        <label for="review-sort" class="small text-body-secondary text-nowrap">Sort by</label>
        <select id="review-sort" class="form-select form-select-sm">
          @for (option of sortOptions; track option.value) {
            <option [value]="option.value">{{ option.label }}</option>
          }
        </select>
      </div>
    </div>

    <div class="review-list">
      @for (review of reviews().content; track review.id) {
        <app-review-card [review]="review" />
      } @empty {
        <p class="text-body-secondary py-3 mb-0">No reviews yet.</p>
      }
    </div>

    <app-pager
      class="d-block mt-3"
      [page]="reviews().page"
      [size]="reviews().size"
      [totalElements]="reviews().totalElements"
      [totalPages]="reviews().totalPages"
    />
  `,
  styles: `
    .review-list > :not(:first-child) {
      border-top: var(--bs-border-width) solid var(--bs-border-color);
    }
  `,
})
export class ProductReviews {
  readonly summary = input.required<RatingSummary>();
  readonly reviews = input.required<PageResponse<Review>>();

  protected readonly starFilters = [5, 4, 3, 2, 1];
  protected readonly sortOptions: { value: ReviewSort; label: string }[] = [
    { value: 'newest', label: 'Newest' },
    { value: 'helpful', label: 'Most helpful' },
  ];
}
