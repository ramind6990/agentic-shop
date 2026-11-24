import { ChangeDetectionStrategy, Component, inject } from '@angular/core';

import { LoadState } from '../../../../shared/ui/load-state/load-state';
import { Pager } from '../../../../shared/ui/pager/pager';
import { ProductCard } from '../../../catalog/components/product-card/product-card';
import { SellerProductsStore } from '../../state/seller-detail.store';

/** Seller products tab: the product grid limited to this seller (GET /api/products?sellerId=), best sellers first. */
@Component({
  selector: 'app-seller-products',
  imports: [LoadState, Pager, ProductCard],
  providers: [SellerProductsStore],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @let products = store.products;
    <app-load-state
      [loading]="products.loading()"
      [error]="products.error()"
      [hasValue]="!!products.value()"
      (retry)="products.reload()"
    >
      @if (products.value(); as page) {
        <div class="row row-cols-1 row-cols-sm-2 row-cols-lg-3 row-cols-xxl-4 g-3 mb-4">
          @for (product of page.content; track product.id) {
            <div class="col"><app-product-card [product]="product" [priority]="$index < 4" /></div>
          } @empty {
            <p class="col text-body-secondary small">This seller has no products.</p>
          }
        </div>
        <app-pager
          [page]="page.page"
          [size]="page.size"
          [totalElements]="page.totalElements"
          [totalPages]="page.totalPages"
          (pageChange)="products.setPage($event)"
        />
      }
    </app-load-state>
  `,
})
export class SellerProducts {
  protected readonly store = inject(SellerProductsStore);
}
