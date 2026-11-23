import { DecimalPipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, computed, inject } from '@angular/core';

import { FilterSection } from '../../../../shared/ui/filter-section/filter-section';
import { LoadState } from '../../../../shared/ui/load-state/load-state';
import { ProductSearchStore } from '../../state/product-search.store';

/**
 * Filter sidebar for product search. Options and counts come from the facets response; what is checked comes from
 * the query (the URL). Every change calls a store action, which writes the URL.
 */
@Component({
  selector: 'app-product-filters',
  imports: [DecimalPipe, FilterSection, LoadState],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './product-filters.html',
  styleUrl: './product-filters.scss',
})
export class ProductFilters {
  protected readonly store = inject(ProductSearchStore);
  protected readonly query = this.store.query;

  protected readonly selectedBrands = computed(() => new Set(this.query().brandIds.map(String)));

  /** Ancestors of the selected category (links up the tree), without the category itself. */
  protected readonly ancestors = computed(
    () => this.store.category()?.breadcrumbs.slice(0, -1) ?? [],
  );

  protected isAttributeSelected(code: string, value: string): boolean {
    return this.query().attrs[code]?.includes(value) ?? false;
  }

  protected rangeValue(code: string, bound: 'min' | 'max'): number | null {
    return this.query().ranges[code]?.[bound] ?? null;
  }

  protected onPriceChange(min: string, max: string): void {
    this.store.setPrice(toNumber(min), toNumber(max));
  }

  protected onRangeChange(code: string, min: string, max: string): void {
    this.store.setAttributeRange(code, toNumber(min), toNumber(max));
  }

  protected onRating(value: string): void {
    this.store.setMinRating(Number(value));
  }

  protected onInStock(event: Event): void {
    this.store.setInStock((event.target as HTMLInputElement).checked);
  }
}

function toNumber(raw: string): number | null {
  if (raw.trim() === '') {
    return null;
  }
  const value = Number(raw);
  return Number.isFinite(value) ? value : null;
}
