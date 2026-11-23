import { DatePipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';

import { enumLabel } from '../../../../shared/utils/enum-label';
import { TicketMessage } from '../../models/ticket.model';

/**
 * One message in a ticket thread. System events render as a centred line; customer, staff and
 * seller messages as cards with a coloured edge per author type; internal notes get a yellow card.
 */
@Component({
  selector: 'app-ticket-message',
  imports: [DatePipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: { class: 'd-block' },
  templateUrl: './ticket-message.html',
  styleUrl: './ticket-message.scss',
})
export class TicketMessageView {
  readonly message = input.required<TicketMessage>();

  protected readonly authorLabel = computed(() => enumLabel(this.message().authorType));
  /** Customer on the left, platform staff and seller indented to the right. */
  protected readonly fromPlatformSide = computed(() => this.message().authorType !== 'CUSTOMER');
}
