import { DatePipe, NgOptimizedImage } from '@angular/common';
import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';

import { Price } from '../../../../shared/ui/price/price';
import { StarRating } from '../../../../shared/ui/star-rating/star-rating';
import { ProductDetail } from '../../models/chat.model';

const SHOWN_ATTRIBUTES = 8;

/** One product in full: picture, price, rating, availability, key attributes and the start of its description. */
@Component({
  selector: 'app-product-detail-block',
  imports: [NgOptimizedImage, DatePipe, Price, StarRating],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @let p = product();
    <article class="card">
      <div class="row g-0">
        <div class="col-sm-4 thumb rounded-start">
          <img
            [ngSrc]="p.card.thumbnailUrl"
            width="240"
            height="240"
            alt=""
            (error)="hide($event)"
          />
        </div>
        <div class="col-sm-8">
          <div class="card-body p-3 d-flex flex-column gap-1">
            <div class="small text-body-secondary">{{ p.card.brand }} · sold by {{ p.seller }}</div>
            <h3 class="h6 mb-0">{{ p.card.title }}</h3>
            @if (p.card.rating !== null) {
              <app-star-rating [value]="p.card.rating" [count]="p.card.ratingCount" />
            }
            <app-price
              [price]="p.card.price"
              [listPrice]="p.card.listPrice"
              [currency]="p.card.currency"
            />
            <div class="small">
              @if (p.delisted) {
                <span class="text-danger-emphasis">No longer sold</span>
              } @else if (p.availableUnits > 0) {
                <span class="text-success-emphasis">In stock</span>
                <span class="text-body-secondary">({{ p.availableUnits }} available)</span>
              } @else {
                <span class="text-warning-emphasis">Out of stock</span>
                @if (p.restockEta) {
                  <span class="text-body-secondary"
                    >· back around {{ p.restockEta | date: 'MMM d' }}</span
                  >
                }
              }
            </div>
            @if (attributes().length) {
              <dl class="attrs small mb-0 mt-1">
                @for (a of attributes(); track a.name) {
                  <dt>{{ a.name }}</dt>
                  <dd>{{ a.value }}</dd>
                }
              </dl>
            }
            @if (p.description) {
              <p class="small text-body-secondary mb-0 mt-1 description">{{ p.description }}</p>
            }
          </div>
        </div>
      </div>
    </article>
  `,
  styles: `
    .thumb {
      overflow: hidden;
      background: var(--bs-secondary-bg);
      min-height: 10rem;

      img {
        width: 100%;
        height: 100%;
        object-fit: cover;
      }
    }

    .attrs {
      display: grid;
      grid-template-columns: max-content 1fr;
      column-gap: 0.75rem;

      dt {
        font-weight: 500;
        color: var(--bs-secondary-color);
      }

      dd {
        margin: 0;
      }
    }

    .description {
      display: -webkit-box;
      -webkit-line-clamp: 3;
      -webkit-box-orient: vertical;
      overflow: hidden;
    }
  `,
})
export class ProductDetailBlock {
  readonly product = input.required<ProductDetail>();

  protected readonly attributes = computed(() =>
    this.product().attributes.slice(0, SHOWN_ATTRIBUTES),
  );

  protected hide(event: Event): void {
    (event.target as HTMLElement).style.visibility = 'hidden';
  }
}
