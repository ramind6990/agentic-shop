// Mock ticket rows until GET /api/tickets is wired.
import {
  MOCK_CUSTOMER_REFS,
  MOCK_SELLER_REFS,
  MOCK_STAFF_REFS,
  hoursAgo,
  pick,
} from '../../../shared/mocks/reference.mock';
import { PageResponse } from '../../../shared/models/api.model';
import { TicketSummary } from '../models/ticket.model';
import { MOCK_TICKET } from './ticket-thread.mock';

const jordan = { id: 3310, name: 'Jordan King' };
const { t1, t1b, t2 } = MOCK_STAFF_REFS;

/** Tickets of customer 3310 (Jordan King), used on the customer page. */
export const MOCK_TICKET_ROWS: TicketSummary[] = [
  MOCK_TICKET,
  {
    id: 47122, ticketNumber: 'TCK-047122', subject: 'Refund status for returned laptop', category: 'RETURN',
    status: 'RESOLVED', priority: 'NORMAL', tier: 'T1', customer: jordan,
    order: { id: 96233, orderNumber: 'AS-260911-000842' }, assignee: t1,
    createdAt: '2026-10-01T12:40:00Z', updatedAt: '2026-10-04T09:15:00Z',
  },
  {
    id: 45310, ticketNumber: 'TCK-045310', subject: 'Card was charged twice', category: 'PAYMENT',
    status: 'CLOSED', priority: 'HIGH', tier: 'T1', customer: jordan,
    order: { id: 93870, orderNumber: 'AS-260702-003105' }, assignee: t1b,
    createdAt: '2026-07-02T20:11:00Z', updatedAt: '2026-07-05T10:02:00Z',
  },
  {
    id: 41207, ticketNumber: 'TCK-041207', subject: 'Cannot reset my password', category: 'ACCOUNT',
    status: 'CLOSED', priority: 'LOW', tier: 'T1', customer: jordan, order: null, assignee: t1b,
    createdAt: '2026-03-19T08:30:00Z', updatedAt: '2026-03-19T11:47:00Z',
  },
];

// Situations from the seed: late or lost parcels, damage, refunds, disputes, payments, cancellations, questions.
const situations: Pick<TicketSummary, 'subject' | 'category' | 'status' | 'priority' | 'tier'>[] = [
  { subject: 'Where is my order?', category: 'SHIPPING', status: 'OPEN', priority: 'NORMAL', tier: 'T1' },
  { subject: 'Item arrived damaged', category: 'RETURN', status: 'PENDING', priority: 'HIGH', tier: 'T1' },
  { subject: 'Seller rejected my return', category: 'RETURN', status: 'ESCALATED', priority: 'URGENT', tier: 'T2' },
  { subject: 'Payment failed but money was taken', category: 'PAYMENT', status: 'OPEN', priority: 'HIGH', tier: 'T1' },
  { subject: 'Please cancel my order', category: 'ORDER', status: 'RESOLVED', priority: 'NORMAL', tier: 'T1' },
  { subject: 'Does this laptop support 2 monitors?', category: 'PRODUCT', status: 'RESOLVED', priority: 'LOW', tier: 'T1' },
  { subject: 'Package arrived a week late', category: 'SHIPPING', status: 'PENDING', priority: 'NORMAL', tier: 'T1' },
  { subject: 'When will I get my refund?', category: 'RETURN', status: 'OPEN', priority: 'NORMAL', tier: 'T1' },
  { subject: 'Change my email address', category: 'ACCOUNT', status: 'CLOSED', priority: 'LOW', tier: 'T1' },
];

const others: TicketSummary[] = Array.from({ length: 18 }, (_, i) => {
  const s = pick(situations, i);
  const id = 48420 - i * 11;
  return {
    id,
    ticketNumber: `TCK-0${id}`,
    ...s,
    customer: pick(MOCK_CUSTOMER_REFS.slice(1), i),
    order:
      s.category === 'ACCOUNT' || s.category === 'PRODUCT'
        ? null
        : { id: 99900 - i * 7, orderNumber: `AS-2611${String(19 - (i % 15)).padStart(2, '0')}-00${4300 - i * 41}` },
    assignee: s.tier === 'T2' ? t2 : s.status === 'OPEN' && i % 3 === 0 ? null : pick([t1, t1b], i),
    createdAt: hoursAgo(10 + i * 14),
    updatedAt: hoursAgo(2 + i * 9),
  };
});

/** First page of the ticket list, most recently updated first. */
export const MOCK_TICKET_PAGE: PageResponse<TicketSummary> = {
  page: 0,
  size: 25,
  totalElements: 15000,
  totalPages: 600,
  content: [MOCK_TICKET, ...others].sort((a, b) => b.updatedAt.localeCompare(a.updatedAt)),
};

/** Assignee choices for the filter until /api/users is wired. */
export const MOCK_ASSIGNEE_OPTIONS = [t1, t1b, t2];
export const MOCK_TICKET_SELLER_OPTIONS = MOCK_SELLER_REFS;
