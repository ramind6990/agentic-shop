/** How a cell value is rendered. */
export type CellType = 'text' | 'number' | 'money' | 'date' | 'datetime' | 'status' | 'role';

/** One column of a DataTable. Every list page (orders, returns, tickets…) is described with these. */
export interface ColumnDef<T> {
  header: string;
  /** Raw value of the cell: text, number, ISO date/instant, or an enum value for `status`. */
  value: (row: T) => string | number | null;
  type?: CellType;
  /** Wrap the value in a router link. */
  link?: (row: T) => string | (string | number)[];
  /** Currency code for `money` cells (default USD). */
  currency?: (row: T) => string;
  /** Monospace text, for codes such as order numbers and SKUs. */
  mono?: boolean;
  /** Secondary line under the value, in muted small text. */
  hint?: (row: T) => string | null;
}
