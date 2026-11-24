import { HttpClient } from '@angular/common/http';
import { Injectable, Signal, computed, inject } from '@angular/core';

import { loadOnChange } from '../../shared/data/load-on-change';
import { SelectOption, enumLabel } from '../../shared/utils/enum-label';

/** Enum name (Java class name, e.g. "OrderStatus") → its values. */
export type EnumMap = Record<string, string[]>;

/**
 * App-wide store for GET /api/meta/enums: every backend enum's values, loaded once and shared by every screen
 * that renders a dropdown. A root singleton, unlike page stores.
 */
@Injectable({ providedIn: 'root' })
export class MetaStore {
  private readonly http = inject(HttpClient);
  private readonly enums$ = loadOnChange(
    () => true,
    () => this.http.get<EnumMap>('/api/meta/enums'),
  );

  readonly enums = this.enums$.value;

  /** Dropdown options for one enum, e.g. options('OrderStatus'). Empty until the enums have loaded. */
  options(enumName: string, exclude: readonly string[] = []): Signal<SelectOption[]> {
    return computed(() =>
      (this.enums()?.[enumName] ?? [])
        .filter((value) => !exclude.includes(value))
        .map((value) => ({ value, label: enumLabel(value) })),
    );
  }
}
