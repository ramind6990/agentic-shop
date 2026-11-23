import { DecimalPipe } from '@angular/common';
import { ChangeDetectionStrategy, Component } from '@angular/core';

import { PageHeader } from '../../../../shared/ui/page-header/page-header';
import { Pager } from '../../../../shared/ui/pager/pager';
import { ProductCard } from '../../components/product-card/product-card';
import { ProductFilters } from '../../components/product-filters/product-filters';
import {
  MOCK_CATEGORY_PATH,
  MOCK_PRODUCT_FACETS,
  MOCK_PRODUCT_PAGE,
} from '../../mocks/product-search.mock';
import { ProductSort } from '../../models/product.model';

/** Product search: filter sidebar, result grid and pager. Uses mock data until the API is wired. */
@Component({
  selector: 'app-product-search',
  imports: [DecimalPipe, PageHeader, Pager, ProductCard, ProductFilters],
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: { class: 'page-fill' },
  templateUrl: './product-search.html',
})
export class ProductSearch {
  protected readonly result = MOCK_PRODUCT_PAGE;
  protected readonly facets = MOCK_PRODUCT_FACETS;
  protected readonly categoryPath = MOCK_CATEGORY_PATH;

  protected readonly sortOptions: { value: ProductSort; label: string }[] = [
    { value: 'relevance', label: 'Relevance' },
    { value: 'bestselling', label: 'Best selling' },
    { value: 'price_asc', label: 'Price: low to high' },
    { value: 'price_desc', label: 'Price: high to low' },
    { value: 'newest', label: 'Newest' },
    { value: 'rating', label: 'Customer rating' },
  ];
}
