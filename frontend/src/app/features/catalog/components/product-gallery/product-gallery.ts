import { NgOptimizedImage } from '@angular/common';
import { ChangeDetectionStrategy, Component, input } from '@angular/core';

import { ProductImage } from '../../models/product.model';

/** Main image with a thumbnail strip. Thumbnails are display-only for now (first image is shown). */
@Component({
  selector: 'app-product-gallery',
  imports: [NgOptimizedImage],
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: { class: 'd-block' },
  template: `
    @if (images().length > 0) {
      <div class="ratio ratio-1x1 placeholder-block overflow-hidden mb-2">
        <img
          [ngSrc]="images()[0].url"
          width="800"
          height="800"
          priority
          class="object-fit-cover"
          [alt]="images()[0].altText"
        />
      </div>
      <ul class="list-unstyled d-flex gap-2 mb-0" aria-label="More images">
        @for (image of images(); track image.url; let first = $first) {
          <li class="thumb" [class.thumb-active]="first">
            <img [ngSrc]="image.url" width="72" height="72" class="object-fit-cover rounded" [alt]="image.altText" />
          </li>
        }
      </ul>
    } @else {
      <div class="ratio ratio-1x1 placeholder-block"></div>
    }
  `,
  styleUrl: './product-gallery.scss',
})
export class ProductGallery {
  readonly images = input.required<ProductImage[]>();
}
