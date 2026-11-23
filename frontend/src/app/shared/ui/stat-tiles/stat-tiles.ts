import { ChangeDetectionStrategy, Component, input } from '@angular/core';

export interface StatTile {
  label: string;
  /** Already formatted (number, money, date…). */
  value: string;
  hint?: string;
  tone?: 'danger' | 'warning' | 'success';
}

/** Row of headline numbers for an overview tab. */
@Component({
  selector: 'app-stat-tiles',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <ul class="row row-cols-2 row-cols-md-4 g-3 list-unstyled mb-0">
      @for (tile of tiles(); track tile.label) {
        <li class="col">
          <div class="panel p-3 h-100">
            <div class="section-label">{{ tile.label }}</div>
            <div class="fs-4 fw-semibold num" [class]="tile.tone ? 'text-' + tile.tone + '-emphasis' : ''">
              {{ tile.value }}
            </div>
            @if (tile.hint) {
              <div class="small text-body-secondary">{{ tile.hint }}</div>
            }
          </div>
        </li>
      }
    </ul>
  `,
})
export class StatTiles {
  readonly tiles = input.required<StatTile[]>();
}
