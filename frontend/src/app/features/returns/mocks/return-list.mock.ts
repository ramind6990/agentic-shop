// Mock rows for the returns and refunds lists until /api/returns and /api/refunds are wired.
import {
  MOCK_CUSTOMER_REFS,
  MOCK_STAFF_REFS,
  hoursAgo,
  pick,
} from '../../../shared/mocks/reference.mock';
import { PageResponse } from '../../../shared/models/api.model';
import { Refund, ReturnReason, ReturnRequest, ReturnStatus } from '../models/return.model';

// Reason depends on what was bought: apparel returns are about fit, electronics about defects.
const items: { product: { id: number; name: string }; reason: ReturnReason; price: number }[] = [
  { product: { id: 22417, name: 'Novatek Glide Wireless Mouse, Graphite' }, reason: 'DEFECTIVE', price: 19.99 },
  { product: { id: 41022, name: 'Maple Street Slim Fit Oxford Shirt, Blue, M' }, reason: 'DOES_NOT_FIT', price: 34.5 },
  { product: { id: 18302, name: 'Lumio Air 13" Laptop, Core i5, 8GB RAM' }, reason: 'NOT_AS_DESCRIBED', price: 449 },
  { product: { id: 52310, name: 'Copper Kettle 10" Nonstick Frying Pan' }, reason: 'DAMAGED', price: 39.99 },
  { product: { id: 30108, name: 'Trailhead Basecamp 3-Person Dome Tent' }, reason: 'ARRIVED_LATE', price: 189 },
  { product: { id: 41187, name: 'Maple Street Chino Pants, Khaki, 32x30' }, reason: 'CHANGED_MIND', price: 42 },
  { product: { id: 18455, name: 'Zentro Flip 14" 2-in-1 Laptop, Core i7' }, reason: 'DEFECTIVE', price: 1099 },
  { product: { id: 41309, name: 'Maple Street Wool Crew Sweater, Grey, L' }, reason: 'WRONG_ITEM', price: 58 },
];

// Newest first: recent returns are still open, older ones resolved.
const statuses: ReturnStatus[] = [
  'REQUESTED', 'REQUESTED', 'APPROVED', 'DISPUTED', 'APPROVED', 'RECEIVED', 'REJECTED', 'RECEIVED',
  'CLOSED', 'CLOSED', 'DISPUTED', 'CLOSED', 'CLOSED', 'REJECTED', 'CLOSED', 'CLOSED', 'CLOSED', 'CLOSED',
];

const open = new Set<ReturnStatus>(['REQUESTED', 'APPROVED', 'DISPUTED', 'RECEIVED']);

const returns: ReturnRequest[] = statuses.map((status, i) => {
  const item = pick(items, i);
  const opened = hoursAgo(6 + i * 19);
  const orderNumber = `AS-26${String(11 - Math.floor(i / 8)).padStart(2, '0')}${String(18 - (i % 8) * 2).padStart(2, '0')}-00${4100 - i * 53}`;
  return {
    id: 15300 - i * 7,
    order: { id: 99800 - i * 41, orderNumber },
    orderItemId: 302000 - i,
    product: item.product,
    customer: pick(MOCK_CUSTOMER_REFS, i * 3),
    quantity: 1,
    reason: item.reason,
    status,
    customerNote: null,
    sellerNote: null,
    openedAt: opened,
    resolvedAt: open.has(status) ? null : hoursAgo(6 + i * 19 - 96),
  };
});

export const MOCK_RETURN_PAGE: PageResponse<ReturnRequest> = {
  page: 0,
  size: 25,
  totalElements: 6118,
  totalPages: 245,
  content: returns,
};

/** Refunds from closed returns, plus a lost parcel (full refund) and a late delivery (goodwill credit). */
const refunds: Refund[] = [
  {
    id: 21140, order: { id: 99504, orderNumber: 'AS-261102-002314' }, returnRequestId: null, amount: 342.18,
    reason: 'Lost parcel: full refund', status: 'PENDING', issuedBy: MOCK_STAFF_REFS.t2, createdAt: hoursAgo(3),
  },
  {
    id: 21122, order: { id: 99611, orderNumber: 'AS-261105-003088' }, returnRequestId: null, amount: 5,
    reason: 'Goodwill credit: late delivery', status: 'ISSUED', issuedBy: MOCK_STAFF_REFS.t1, createdAt: hoursAgo(20),
  },
  ...returns
    .filter((r) => r.status === 'CLOSED')
    .map((r, i): Refund => {
      const amount = Math.round(items.find((it) => it.product.id === r.product.id)!.price * 1.0825 * 100) / 100;
      return {
        id: 21100 - i * 13,
        order: r.order,
        returnRequestId: r.id,
        amount,
        reason: 'Return: ' + r.reason.replaceAll('_', ' ').toLowerCase(),
        status: i === 3 ? 'FAILED' : 'ISSUED',
        issuedBy: amount > 100 ? MOCK_STAFF_REFS.t2 : MOCK_STAFF_REFS.t1,
        createdAt: r.resolvedAt ?? r.openedAt,
      };
    }),
];

export const MOCK_REFUND_PAGE: PageResponse<Refund> = {
  page: 0,
  size: 25,
  totalElements: 7402,
  totalPages: 297,
  content: refunds,
};
