// Mock data for the order list until it is wired to GET /api/orders.
// Newest first, like the API; recent orders are still pending or in transit, older ones delivered.
import { PageResponse, Ref } from '../../../shared/models/api.model';
import { OrderStatus, OrderSummary } from '../models/order.model';

const customers: Ref[] = [
  { id: 1204, name: 'Emily Roberts' },
  { id: 877, name: 'Lukas Meyer' },
  { id: 15021, name: 'Priya Shah' },
  { id: 3310, name: 'Jordan King' },
  { id: 9142, name: 'Chloé Tremblay' },
  { id: 6620, name: 'Tom Hughes' },
  { id: 12007, name: 'Anna Schmidt' },
  { id: 4588, name: 'Marcus Lee' },
];

const sellers: (Ref & { currency: string })[] = [
  { id: 41, name: 'Maple Street Electronics', currency: 'USD' },
  { id: 112, name: 'Harbor Tech Supply', currency: 'USD' },
  { id: 58, name: 'Maple Street Apparel Outlet', currency: 'USD' },
  { id: 203, name: 'Northgate Digital', currency: 'USD' },
  { id: 317, name: 'Copper Kettle Kitchenware', currency: 'USD' },
  { id: 266, name: 'Trailhead Outdoor Co.', currency: 'USD' },
];

// Status by age: index 0 is the newest order.
const statuses: OrderStatus[] = [
  'PENDING', 'PAID', 'PAID', 'SHIPPED', 'SHIPPED', 'SHIPPED', 'DELIVERED', 'CANCELLED',
  'DELIVERED', 'DELIVERED', 'PARTIALLY_REFUNDED', 'DELIVERED', 'DELIVERED', 'RETURNED', 'DELIVERED',
  'REFUNDED', 'DELIVERED', 'DELIVERED', 'DELIVERED', 'PARTIALLY_REFUNDED', 'DELIVERED', 'DELIVERED',
  'DELIVERED', 'DELIVERED', 'DELIVERED',
];

const totals = [
  128.4, 54.99, 1012.35, 89.0, 23.5, 249.97, 64.2, 18.99, 412.0, 76.45, 1649.0, 35.8, 142.1,
  59.99, 899.0, 27.3, 311.6, 45.0, 188.25, 74.9, 2399.0, 12.99, 96.4, 530.75, 61.2,
];

const start = Date.parse('2026-11-19T21:40:00Z');

export const MOCK_ORDER_PAGE: PageResponse<OrderSummary> = {
  page: 0,
  size: 25,
  totalElements: 100000,
  totalPages: 4000,
  content: statuses.map((status, i) => {
    const placed = new Date(start - i * 5.3 * 3600_000);
    const yymmdd = placed.toISOString().slice(2, 10).replaceAll('-', '');
    const seller = sellers[(i * 5) % sellers.length];
    return {
      id: 100000 - i,
      orderNumber: `AS-${yymmdd}-${String(4521 - i * 37).padStart(6, '0')}`,
      status,
      placedAt: placed.toISOString(),
      total: totals[i],
      currency: seller.currency,
      customer: customers[(i * 3) % customers.length],
      seller: { id: seller.id, name: seller.name },
      itemCount: 1 + ((i * 7) % 4),
    };
  }),
};

/** Seller choices for the filter until /api/sellers is wired. */
export const MOCK_SELLER_OPTIONS: Ref[] = sellers.map(({ id, name }) => ({ id, name }));
