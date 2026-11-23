import {
  ChangeDetectionStrategy,
  Component,
  ElementRef,
  computed,
  inject,
  viewChild,
} from '@angular/core';

import { LoadState } from '../../../../shared/ui/load-state/load-state';
import { PageHeader } from '../../../../shared/ui/page-header/page-header';
import { Pager } from '../../../../shared/ui/pager/pager';
import { ProductCard } from '../../components/product-card/product-card';
import { ProductFilters } from '../../components/product-filters/product-filters';
import { ProductSort } from '../../models/product.model';
import { ProductSearchStore } from '../../state/product-search.store';

/** Product search: filter sidebar, result grid and pager. All state lives in the URL via ProductSearchStore. */
@Component({
  selector: 'app-product-search',
  imports: [LoadState, PageHeader, Pager, ProductCard, ProductFilters],
  providers: [ProductSearchStore],
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: { class: 'page-fill' },
  templateUrl: './product-search.html',
})
export class ProductSearch {
  protected readonly store = inject(ProductSearchStore);
  private readonly resultsPane = viewChild.required<ElementRef<HTMLElement>>('resultsPane');

  protected readonly sortOptions: { value: ProductSort; label: string }[] = [
    { value: 'relevance', label: 'Relevance' },
    { value: 'bestselling', label: 'Best selling' },
    { value: 'price_asc', label: 'Price: low to high' },
    { value: 'price_desc', label: 'Price: high to low' },
    { value: 'newest', label: 'Newest' },
    { value: 'rating', label: 'Customer rating' },
  ];

  protected readonly subtitle = computed(() => {
    const path = this.store.category()?.breadcrumbs.map((c) => c.name) ?? ['All categories'];
    const total = this.store.results()?.totalElements;
    return total === undefined
      ? path.join(' › ')
      : `${path.join(' › ')} · ${total.toLocaleString()} results`;
  });

  protected onSearch(event: Event, text: string): void {
    event.preventDefault();
    this.store.search(text);
  }

  protected onSort(event: Event): void {
    this.store.setSort((event.target as HTMLSelectElement).value as ProductSort);
  }

  protected onPage(page: number): void {
    this.store.setPage(page);
    this.resultsPane().nativeElement.scrollTop = 0;
  }
}
