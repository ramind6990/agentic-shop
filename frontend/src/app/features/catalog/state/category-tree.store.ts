import { Injectable, computed, inject } from '@angular/core';

import { loadOnChange } from '../../../shared/data/load-on-change';
import { CategoryApi } from '../data-access/category-api';
import { CategoryNode } from '../models/category.model';

export interface CategoryOption {
  id: number;
  /** Indented by level, for a <select>. */
  label: string;
}

/**
 * App-wide cache of the category tree (GET /api/categories). The taxonomy never changes at runtime, so it is
 * loaded once and shared by the category browser and the category dropdowns (brands filter).
 */
@Injectable({ providedIn: 'root' })
export class CategoryTreeStore {
  private readonly api = inject(CategoryApi);
  private readonly tree$ = loadOnChange(
    () => true,
    () => this.api.tree(),
  );

  readonly tree = this.tree$.value;
  readonly loading = this.tree$.loading;
  readonly error = this.tree$.error;

  /** Every category, depth-first, for dropdowns. */
  readonly options = computed<CategoryOption[]>(() => flatten(this.tree() ?? []));

  reload(): void {
    this.tree$.reload();
  }
}

function flatten(nodes: CategoryNode[], depth = 0): CategoryOption[] {
  return nodes.flatMap((node) => [
    // Non-breaking spaces: a <select> collapses ordinary ones.
    { id: node.id, label: `${'\u00a0\u00a0'.repeat(depth)}${node.name}` },
    ...flatten(node.children, depth + 1),
  ]);
}
