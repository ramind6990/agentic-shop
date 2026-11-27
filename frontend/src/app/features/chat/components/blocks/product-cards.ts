import { NgOptimizedImage } from '@angular/common';
import { ChangeDetectionStrategy, Component, input } from '@angular/core';

import { Price } from '../../../../shared/ui/price/price';
import { StarRating } from '../../../../shared/ui/star-rating/star-rating';
import { ProductItem } from '../../models/chat.model';

/** The products of a reply, as small cards (as many per row as fit). */
@Component({
  selector: 'app-product-cards',
  imports: [NgOptimizedImage, Price, StarRating],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <ul class="block-grid" style="--block-min: 8.5rem">
      @for (p of items(); track p.id) {
        <li class="block-card product">
          <div class="thumb">
            <img [ngSrc]="p.thumbnailUrl" width="160" height="160" alt="" (error)="hide($event)" />
            @if (!p.inStock) {
              <span class="badge text-bg-secondary stock">Out of stock</span>
            }
          </div>
          <div class="info">
            <div class="brand text-truncate">{{ p.brand }}</div>
            <h3 class="title line-clamp" [title]="p.title">{{ p.title }}</h3>
            @if (p.rating !== null) {
              <app-star-rating class="rating" [value]="p.rating" [count]="p.ratingCount" />
            }
            <app-price
              class="price"
              [price]="p.price"
              [listPrice]="p.listPrice"
              [currency]="p.currency"
            />
          </div>
        </li>
      }
    </ul>
  `,
  styles: `
    .product {
      padding: 0;
      gap: 0;
      overflow: hidden;
    }

    .thumb {
      position: relative;
      aspect-ratio: 1;
      background: var(--bs-secondary-bg);

      img {
        width: 100%;
        height: 100%;
        object-fit: cover;
      }
    }

    .stock {
      position: absolute;
      top: 0.35rem;
      left: 0.35rem;
      font-size: 0.6875rem;
    }

    .info {
      flex: 1 1 auto;
      display: flex;
      flex-direction: column;
      gap: 0.15rem;
      padding: 0.45rem 0.55rem 0.55rem;
    }

    .brand {
      font-size: 0.6875rem;
      letter-spacing: 0.03em;
      text-transform: uppercase;
      color: var(--bs-secondary-color);
    }

    .title {
      margin: 0;
      font-size: 0.8125rem;
      font-weight: 600;
      line-height: 1.3;
    }

    .rating {
      font-size: 0.75rem;
    }

    .price {
      margin-top: auto;
      padding-top: 0.2rem;
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
