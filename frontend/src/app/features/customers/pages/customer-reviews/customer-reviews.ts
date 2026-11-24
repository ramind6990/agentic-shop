import { DatePipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { RouterLink } from '@angular/router';

import { PagedSection } from '../../../../shared/ui/paged-section/paged-section';
import { StarRating } from '../../../../shared/ui/star-rating/star-rating';
import { CustomerReviewsStore } from '../../state/customer-detail.store';

/** Reviews the customer wrote, each with the product it is about (GET /api/customers/{id}/reviews). */
@Component({
  selector: 'app-customer-reviews',
  imports: [DatePipe, RouterLink, PagedSection, StarRating],
  providers: [CustomerReviewsStore],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @let reviews = store.reviews;
    <app-paged-section
      title="Reviews written"
      [flush]="false"
      [result]="reviews.value()"
      [loading]="reviews.loading()"
      [error]="reviews.error()"
      (pageChange)="reviews.setPage($event)"
      (retry)="reviews.reload()"
    >
      @for (review of reviews.rows(); track review.id; let last = $last) {
        <article
          class="small"
          [class.border-bottom]="!last"
          [class.pb-3]="!last"
          [class.mb-3]="!last"
        >
          <a class="d-block mb-1" [routerLink]="['/products', review.product.id]">{{
            review.product.name
          }}</a>
          <div class="d-flex flex-wrap align-items-center gap-2 mb-1">
            <app-star-rating [value]="review.rating" />
            <h3 class="h6 mb-0">{{ review.title }}</h3>
            <time class="ms-auto text-body-secondary" [attr.datetime]="review.createdAt">
              {{ review.createdAt | date: 'mediumDate' }}
            </time>
          </div>
          <p class="mb-0">{{ review.body }}</p>
        </article>
      } @empty {
        <p class="small text-body-secondary mb-0">No reviews written.</p>
      }
    </app-paged-section>
  `,
})
export class CustomerReviews {
  protected readonly store = inject(CustomerReviewsStore);
}
