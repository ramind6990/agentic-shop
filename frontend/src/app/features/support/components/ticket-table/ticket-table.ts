import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';

import { ColumnDef } from '../../../../shared/ui/data-table/column-def';
import { DataTable } from '../../../../shared/ui/data-table/data-table';
import { enumLabel } from '../../../../shared/utils/enum-label';
import { TicketSummary } from '../../models/ticket.model';

const ALL_COLUMNS: (ColumnDef<TicketSummary> & { key: string })[] = [
  {
    key: 'ticket', header: 'Ticket', value: (t) => t.subject, link: (t) => ['/tickets', t.id],
    hint: (t) => `${t.ticketNumber} · ${enumLabel(t.category)}`,
  },
  { key: 'customer', header: 'Customer', value: (t) => t.customer.name, link: (t) => ['/customers', t.customer.id] },
  {
    key: 'order', header: 'Order', value: (t) => t.order?.orderNumber ?? null, mono: true,
    link: (t) => ['/orders', t.order?.orderNumber ?? ''],
  },
  { key: 'status', header: 'Status', value: (t) => t.status, type: 'status' },
  { key: 'priority', header: 'Priority', value: (t) => t.priority, type: 'status' },
  { key: 'tier', header: 'Tier', value: (t) => t.tier },
  { key: 'assignee', header: 'Assignee', value: (t) => t.assignee?.name ?? null },
  { key: 'updated', header: 'Updated', value: (t) => t.updatedAt, type: 'datetime' },
];

/** Ticket rows, shared by the ticket list and the customer/seller ticket tabs. */
@Component({
  selector: 'app-ticket-table',
  imports: [DataTable],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <app-data-table
      caption="Support tickets, most recently updated first"
      emptyText="No tickets."
      [rows]="tickets()"
      [columns]="columns()"
      [rowId]="rowId"
    />
  `,
})
export class TicketTable {
  readonly tickets = input.required<TicketSummary[]>();
  /** Column keys to leave out, e.g. ['customer'] on a customer page. */
  readonly hide = input<string[]>([]);

  protected readonly rowId = (t: TicketSummary) => t.id;
  protected readonly columns = computed(() => ALL_COLUMNS.filter((c) => !this.hide().includes(c.key)));
}
