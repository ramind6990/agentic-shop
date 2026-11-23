// Mock data for the ticket thread until it is wired to /api/tickets/{id} and its messages.
// Scenario: parcel lost in transit, Tier-1 opens a carrier trace, the refund is over the Tier-1
// limit so it escalates to Tier-2, the seller weighs in, and staff leave internal notes.
import { PageResponse } from '../../../shared/models/api.model';
import { TicketDetail, TicketMessage } from '../models/ticket.model';

const customer = { id: 3310, name: 'Jordan King' };
const t1 = { id: 31, name: 'Dana Whitfield' };
const t2 = { id: 18, name: 'Marco Ruiz' };
const seller = { id: 266, name: 'Trailhead Outdoor Co.' };
const order = 'AS-261102-002314';
const tracking = '1Z7Y03W60411958823';

export const MOCK_TICKET: TicketDetail = {
  id: 48213,
  ticketNumber: 'TCK-048213',
  subject: `Order ${order} never arrived`,
  category: 'SHIPPING',
  status: 'ESCALATED',
  priority: 'HIGH',
  tier: 'T2',
  customer,
  order: { id: 99504, orderNumber: order },
  seller,
  assignee: t2,
  messageCount: 9,
  createdAt: '2026-11-10T08:14:00Z',
  updatedAt: '2026-11-19T15:37:00Z',
};

const messages: Omit<TicketMessage, 'id'>[] = [
  {
    authorType: 'CUSTOMER', author: customer, internal: false, createdAt: '2026-11-10T08:14:00Z',
    body: `My order ${order} has not arrived and UPS tracking ${tracking} hasn't moved in over a week. I think it's lost.`,
  },
  {
    authorType: 'SYSTEM', author: null, internal: false, createdAt: '2026-11-10T08:14:05Z',
    body: 'Ticket assigned to Dana Whitfield (Tier 1).',
  },
  {
    authorType: 'STAFF', author: t1, internal: false, createdAt: '2026-11-10T10:02:00Z',
    body: `Hi Jordan, I'm sorry about this. I've opened a trace with UPS for tracking number ${tracking}. Carriers usually respond within 2 business days. - Dana`,
  },
  {
    authorType: 'STAFF', author: t1, internal: true, createdAt: '2026-11-10T10:04:00Z',
    body: 'UPS trace case 7731-0492 opened. Last scan Nov 3 at the Reno hub. If there is no new scan by Nov 13, treat as lost.',
  },
  {
    authorType: 'CUSTOMER', author: customer, internal: false, createdAt: '2026-11-14T19:26:00Z',
    body: "Still nothing. It's been almost two weeks. I'd like a refund for the tent and sleeping bags, please.",
  },
  {
    authorType: 'SYSTEM', author: null, internal: false, createdAt: '2026-11-15T09:00:00Z',
    body: 'Escalated to Tier 2: requested refund of $342.18 is above the Tier 1 limit of $100.',
  },
  {
    authorType: 'SELLER', author: seller, internal: false, createdAt: '2026-11-17T13:48:00Z',
    body: 'UPS confirmed the parcel was not scanned after the Reno hub. We can reship today or accept a full refund, whichever the customer prefers.',
  },
  {
    authorType: 'STAFF', author: t2, internal: true, createdAt: '2026-11-19T15:20:00Z',
    body: 'Customer has two other lost-parcel refunds this year. Check account flags before approving; otherwise the claim looks legitimate.',
  },
  {
    authorType: 'STAFF', author: t2, internal: false, createdAt: '2026-11-19T15:37:00Z',
    body: `Hi Jordan, UPS has confirmed the parcel is lost. The seller can reship the order today, or we can refund the full $342.18 to your card. Which would you prefer? - Marco`,
  },
];

export const MOCK_TICKET_MESSAGES: PageResponse<TicketMessage> = {
  page: 0,
  size: 50,
  totalElements: messages.length,
  totalPages: 1,
  content: messages.map((m, i) => ({ id: 510000 + i, ...m })),
};
