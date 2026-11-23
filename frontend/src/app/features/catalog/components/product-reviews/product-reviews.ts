import { ChangeDetectionStrategy, Component, inject } from '@angular/core';

import { LoadState } from '../../../../shared/ui/load-state/load-state';
import { Pager } from '../../../../shared/ui/pager/pager';
import { ReviewSort } from '../../models/product.model';
import { ProductDetailStore } from '../../state/product-detail.store';
import { RatingBreakdown } from '../rating-breakdown/rating-breakdown';
import { ReviewCard } from '../review-card/review-card';

/** Rating breakdown, star filter and sort, then the paged review list. State comes from ProductDetailStore. */
@Component({
  selector: 'app-product-reviews',
  imports: [LoadState, Pager, RatingBreakdown, ReviewCard],
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: { class: 'd-block' },
  template: `
    @if (store.ratingSummary(); as summary) {
      <app-rating-breakdown [summary]="summary" />
    }

    <div class="d-flex flex-wrap align-items-center gap-2 border-top border-bottom py-2 my-3">
      <div class="btn-group btn-group-sm" role="group" aria-label="Filter by rating">
        <button
          type="button"
          class="btn btn-outline-secondary"
          [class.active]="store.reviewQuery().rating === null"
          [attr.aria-pressed]="store.reviewQuery().rating === null"
          (click)="store.setReviewRating(null)"
        >
          All
        </button>
        @for (stars of starFilters; track stars) {
          <button
            type="button"
            class="btn btn-outline-secondary"
            [class.active]="store.reviewQuery().rating === stars"
            [attr.aria-pressed]="store.reviewQuery().rating === stars"
            (click)="store.setReviewRating(stars)"
          >
            {{ stars }} ★
          </button>
        }
      </div>
      <div class="ms-auto d-flex align-items-center gap-2">
        <label for="review-sort" class="small text-body-secondary text-nowrap">Sort by</label>
        <select id="review-sort" class="form-select form-select-sm" (change)="onSort($event)">
          @for (option of sortOptions; track option.value) {
            <option [value]="option.value" [selected]="option.value === store.reviewQuery().sort">
              {{ option.label }}
            </option>
          }
        </select>
      </div>
    </div>

    <app-load-state
      [loading]="store.reviewsLoading()"
      [error]="store.reviewsError()"
      [hasValue]="!!store.reviews()"
      (retry)="store.reloadReviews()"
    >
      @if (store.reviews(); as reviews) {
        <div class="review-list">
          @for (review of reviews.content; track review.id) {
            <app-review-card [review]="review" />
          } @empty {
            <p class="text-body-secondary py-3 mb-0">No reviews match.</p>
          }
        </div>

        @if (reviews.totalPages > 1) {
          <app-pager
            class="d-block mt-3"
            [page]="reviews.page"
            [size]="reviews.size"
            [totalElements]="reviews.totalElements"
            [totalPages]="reviews.totalPages"
            (pageChange)="store.setReviewPage($event)"
          />
        }
      }
    </app-load-state>
  `,
  styles: `
    .review-list > :not(:first-child) {
      border-top: var(--bs-border-width) solid var(--bs-border-color);
    }
  `,
})
export class ProductReviews {
  protected readonly store = inject(ProductDetailStore);

  protected readonly starFilters = [5, 4, 3, 2, 1];
  protected readonly sortOptions: { value: ReviewSort; label: string }[] = [
    { value: 'newest', label: 'Newest' },
    { value: 'helpful', label: 'Most helpful' },
  ];

  protected onSort(event: Event): void {
    this.store.setReviewSort((event.target as HTMLSelectElement).value as ReviewSort);
  }
}
