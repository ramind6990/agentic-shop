import { NgOptimizedImage } from '@angular/common';
import { ChangeDetectionStrategy, Component, input } from '@angular/core';

import { Price } from '../../../../shared/ui/price/price';
import { StarRating } from '../../../../shared/ui/star-rating/star-rating';
import { ProductItem } from '../../models/chat.model';

/** The products of a reply, as small cards. */
@Component({
  selector: 'app-product-cards',
  imports: [NgOptimizedImage, Price, StarRating],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <ul class="row row-cols-2 row-cols-lg-3 g-2 list-unstyled mb-0">
      @for (p of items(); track p.id) {
        <li class="col">
          <article class="card h-100">
            <div class="thumb rounded-top position-relative">
              <img
                [ngSrc]="p.thumbnailUrl"
                width="240"
                height="160"
                alt=""
                (error)="hide($event)"
              />
              @if (!p.inStock) {
                <span class="badge text-bg-secondary position-absolute top-0 start-0 m-2">
                  Out of stock
                </span>
              }
            </div>
            <div class="card-body p-2 d-flex flex-column gap-1">
              <div class="small text-body-secondary">{{ p.brand }}</div>
              <h3 class="h6 mb-0 title">{{ p.title }}</h3>
              @if (p.rating !== null) {
                <app-star-rating [value]="p.rating" [count]="p.ratingCount" />
              }
              <app-price
                class="mt-auto pt-1"
                [price]="p.price"
                [listPrice]="p.listPrice"
                [currency]="p.currency"
              />
            </div>
          </article>
        </li>
      }
    </ul>
  `,
  styles: `
    .thumb {
      aspect-ratio: 3 / 2;
      overflow: hidden;
      background: var(--bs-secondary-bg);

      img {
        width: 100%;
        height: 100%;
        object-fit: cover;
      }
    }

    .title {
      font-size: 0.875rem;
    }
  `,
})
export class ProductCards {
  readonly items = input.required<ProductItem[]>();

  /** Mock thumbnails may not exist: keep the grey placeholder instead of a broken image. */
  protected hide(event: Event): void {
    (event.target as HTMLElement).style.visibility = 'hidden';
  }
}
