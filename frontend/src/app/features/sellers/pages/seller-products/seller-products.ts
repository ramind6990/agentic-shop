import { ChangeDetectionStrategy, Component } from '@angular/core';

import { Pager } from '../../../../shared/ui/pager/pager';
import { ProductCard } from '../../../catalog/components/product-card/product-card';
import { MOCK_PRODUCT_PAGE } from '../../../catalog/mocks/product-search.mock';
import { MOCK_SELLER } from '../../mocks/seller-detail.mock';

/** Seller products tab: the product search grid limited to this seller (GET /api/products?sellerId=). */
@Component({
  selector: 'app-seller-products',
  imports: [Pager, ProductCard],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="row row-cols-1 row-cols-sm-2 row-cols-lg-3 row-cols-xxl-4 g-3 mb-4">
      @for (product of products; track product.id) {
        <div class="col"><app-product-card [product]="product" /></div>
      }
    </div>
    <app-pager [page]="0" [size]="products.length" [totalElements]="total" [totalPages]="totalPages" />
  `,
})
export class SellerProducts {
  protected readonly products = MOCK_PRODUCT_PAGE.content.filter((p) => p.seller.id === MOCK_SELLER.id);
  protected readonly total = MOCK_SELLER.stats.productCount;
  protected readonly totalPages = Math.ceil(this.total / Math.max(1, this.products.length));
}
