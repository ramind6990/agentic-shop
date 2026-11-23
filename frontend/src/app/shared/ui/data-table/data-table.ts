import { CurrencyPipe, DatePipe, DecimalPipe, NgTemplateOutlet } from '@angular/common';
import { ChangeDetectionStrategy, Component, input } from '@angular/core';
import { RouterLink } from '@angular/router';

import { RoleBadge } from '../role-badge/role-badge';
import { StatusBadge } from '../status-badge/status-badge';
import { ColumnDef } from './column-def';

/** Read-only table driven by column definitions. Header row sticks when the table scrolls. */
@Component({
  selector: 'app-data-table',
  imports: [CurrencyPipe, DatePipe, DecimalPipe, NgTemplateOutlet, RouterLink, RoleBadge, StatusBadge],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './data-table.html',
  styleUrl: './data-table.scss',
})
export class DataTable<T> {
  readonly rows = input.required<T[]>();
  readonly columns = input.required<ColumnDef<T>[]>();
  /** Stable identity of a row, used for tracking. */
  readonly rowId = input.required<(row: T) => string | number>();
  /** Accessible table description. */
  readonly caption = input.required<string>();
  readonly emptyText = input('No results match these filters.');

  protected isRight(column: ColumnDef<T>): boolean {
    return column.type === 'number' || column.type === 'money';
  }
}
