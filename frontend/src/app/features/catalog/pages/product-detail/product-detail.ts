import { ChangeDetectionStrategy, Component, input } from '@angular/core';
import { DatePipe, DecimalPipe } from '@angular/common';
import { RouterLink } from '@angular/router';

import { Breadcrumbs, Crumb } from '../../../../shared/ui/breadcrumbs/breadcrumbs';
import { Price } from '../../../../shared/ui/price/price';
import { SectionCard } from '../../../../shared/ui/section-card/section-card';
import { StarRating } from '../../../../shared/ui/star-rating/star-rating';
import { StatusBadge } from '../../../../shared/ui/status-badge/status-badge';
import { ProductAttributes } from '../../components/product-attributes/product-attributes';
import { ProductGallery } from '../../components/product-gallery/product-gallery';
import { ProductReviews } from '../../components/product-reviews/product-reviews';
import {
  MOCK_PRODUCT_DETAIL,
  MOCK_RATING_SUMMARY,
  MOCK_REVIEW_PAGE,
} from '../../mocks/product-detail.mock';

/** Product detail: gallery, buy-box style summary, description, specifications and reviews. Mock data for now. */
@Component({
  selector: 'app-product-detail',
  imports: [
    DatePipe,
    DecimalPipe,
    RouterLink,
    Breadcrumbs,
    Price,
    SectionCard,
    StarRating,
    StatusBadge,
    ProductAttributes,
    ProductGallery,
    ProductReviews,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './product-detail.html',
  styleUrl: './product-detail.scss',
})
export class ProductDetailPage {
  /** Route parameter; unused until the API is wired (the mock product is always shown). */
  readonly id = input<string>();

  protected readonly product = MOCK_PRODUCT_DETAIL;
  protected readonly ratingSummary = MOCK_RATING_SUMMARY;
  protected readonly reviews = MOCK_REVIEW_PAGE;

  protected readonly crumbs: Crumb[] = [
    { label: 'Products', link: '/products' },
    ...this.product.breadcrumbs.map((c) => ({ label: c.name, link: '/products' })),
    { label: this.product.sku },
  ];
}
