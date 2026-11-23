import { DecimalPipe, NgOptimizedImage } from '@angular/common';
import { ChangeDetectionStrategy, Component, input } from '@angular/core';
import { RouterLink } from '@angular/router';

import { Price } from '../../../../shared/ui/price/price';
import { StarRating } from '../../../../shared/ui/star-rating/star-rating';
import { ProductSummary } from '../../models/product.model';

@Component({
  selector: 'app-product-card',
  imports: [NgOptimizedImage, RouterLink, DecimalPipe, Price, StarRating],
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: { class: 'd-block h-100' },
  template: `
    <article class="card h-100">
      <div class="ratio ratio-1x1 placeholder-block rounded-bottom-0">
        <img
          [ngSrc]="product().thumbnailUrl"
          width="400"
          height="400"
          alt=""
          class="object-fit-cover"
          [priority]="priority()"
        />
      </div>
      <div class="card-body d-flex flex-column gap-1 p-3">
        <div class="small text-body-secondary">{{ product().brand.name }}</div>
        <h2 class="h6 card-title mb-1 title-clamp">
          <a [routerLink]="['/products', product().id]" class="stretched-link text-body text-decoration-none">
            {{ product().title }}
          </a>
        </h2>
        <app-star-rating [value]="product().ratingAvg" [count]="product().ratingCount" />
        <app-price
          class="mt-auto pt-2"
          [price]="product().price"
          [listPrice]="product().listPrice"
          [currency]="product().currency"
        />
        <div class="d-flex justify-content-between small">
          @if (product().inStock) {
            <span class="text-success-emphasis">In stock</span>
          } @else {
            <span class="text-danger-emphasis">Out of stock</span>
          }
          <span class="text-body-secondary num">{{ product().salesCount | number }} sold</span>
        </div>
        <div class="small text-body-secondary text-truncate">Sold by {{ product().seller.name }}</div>
      </div>
    </article>
  `,
  styleUrl: './product-card.scss',
})
export class ProductCard {
  readonly product = input.required<ProductSummary>();
  /** Load the image eagerly (cards in the first visible row). */
  readonly priority = input(false);
}
