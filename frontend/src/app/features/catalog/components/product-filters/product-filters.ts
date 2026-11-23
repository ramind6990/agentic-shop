import { DecimalPipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, input } from '@angular/core';

import { FilterSection } from '../../../../shared/ui/filter-section/filter-section';
import { ProductFacets } from '../../models/product.model';

/** Filter sidebar for product search, driven entirely by the facets response. Display-only for now. */
@Component({
  selector: 'app-product-filters',
  imports: [DecimalPipe, FilterSection],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './product-filters.html',
  styleUrl: './product-filters.scss',
})
export class ProductFilters {
  readonly facets = input.required<ProductFacets>();
  /** Names from the root category down to the one being browsed. */
  readonly categoryPath = input<string[]>([]);
}
