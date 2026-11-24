import { CurrencyPipe, DatePipe, DecimalPipe, NgTemplateOutlet } from '@angular/common';
import { ChangeDetectionStrategy, Component, inject, input } from '@angular/core';
import { RouterLink } from '@angular/router';

import { AuthStore } from '../../../core/auth/auth.store';
import { RoleBadge } from '../role-badge/role-badge';
import { StatusBadge } from '../status-badge/status-badge';
import { ColumnDef } from './column-def';

/**
 * Read-only table driven by column definitions. Header row sticks when the table scrolls. A column link is shown
 * as plain text when the signed-in user may not open it (e.g. a customer column for a seller user).
 */
@Component({
  selector: 'app-data-table',
  imports: [
    CurrencyPipe,
    DatePipe,
    DecimalPipe,
    NgTemplateOutlet,
    RouterLink,
    RoleBadge,
    StatusBadge,
  ],
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

  private readonly auth = inject(AuthStore);

  protected isLinked(row: T, column: ColumnDef<T>): boolean {
    return !!column.link && column.value(row) !== null && this.auth.canOpen(column.link(row));
  }

  protected isRight(column: ColumnDef<T>): boolean {
    return column.type === 'number' || column.type === 'money';
  }
}
