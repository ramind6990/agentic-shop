import { ChangeDetectionStrategy, Component, computed, inject } from '@angular/core';
import { DatePipe, DecimalPipe } from '@angular/common';
import { RouterLink } from '@angular/router';

import { Breadcrumbs, Crumb } from '../../../../shared/ui/breadcrumbs/breadcrumbs';
import { LoadState } from '../../../../shared/ui/load-state/load-state';
import { Price } from '../../../../shared/ui/price/price';
import { SectionCard } from '../../../../shared/ui/section-card/section-card';
import { StarRating } from '../../../../shared/ui/star-rating/star-rating';
import { StatusBadge } from '../../../../shared/ui/status-badge/status-badge';
import { ProductAttributes } from '../../components/product-attributes/product-attributes';
import { ProductGallery } from '../../components/product-gallery/product-gallery';
import { ProductReviews } from '../../components/product-reviews/product-reviews';
import { ProductDetailStore } from '../../state/product-detail.store';
import { AccessLink } from '../../../../shared/ui/access-link/access-link';

/** Product detail: gallery, buy-box style summary, description, specifications and reviews. */
@Component({
  selector: 'app-product-detail',
  imports: [
    DatePipe,
    DecimalPipe,
    RouterLink,
    Breadcrumbs,
    LoadState,
    Price,
    SectionCard,
    StarRating,
    StatusBadge,
    ProductAttributes,
    ProductGallery,
    ProductReviews,
    AccessLink,
  ],
  providers: [ProductDetailStore],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './product-detail.html',
  styleUrl: './product-detail.scss',
})
export class ProductDetailPage {
  protected readonly store = inject(ProductDetailStore);

  /** Products › category path (each links to the search filtered on it) › SKU. */
  protected readonly crumbs = computed<Crumb[]>(() => {
    const product = this.store.product();
    if (!product) {
      return [];
    }
    return [
      { label: 'Products', link: '/products' },
      ...product.breadcrumbs.map((c) => ({
        label: c.name,
        link: '/products',
        queryParams: { categoryId: c.id },
      })),
      { label: product.sku },
    ];
  });
}
