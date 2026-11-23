// Mock data for the order detail screen until it is wired to GET /api/orders/{idOrNumber}.
// Scenario: laptop + two mice, card declined once then paid, UPS delivered a day late,
// one mouse returned as defective and refunded by Tier-1 support.
import { OrderDetail } from '../models/order.model';

const order = { id: 98712, orderNumber: 'AS-261024-001877' };
const customer = { id: 1204, name: 'Emily Roberts' };
const mouse = { id: 22417, name: 'Novatek Glide Wireless Mouse, 2.4GHz, Silent Click, Graphite' };

export const MOCK_ORDER_DETAIL: OrderDetail = {
  ...order,
  status: 'PARTIALLY_REFUNDED',
  placedAt: '2026-10-24T18:42:00Z',
  customer,
  seller: { id: 41, name: 'Maple Street Electronics' },
  shippingAddress: {
    recipient: 'Emily Roberts',
    line1: '2148 Alder Grove Lane',
    line2: 'Apt 3B',
    city: 'Sacramento',
    region: 'CA',
    postalCode: '95816',
    country: 'US',
  },
  subtotal: 938.98,
  shippingFee: 0,
  tax: 77.47,
  total: 1016.45,
  currency: 'USD',
  items: [
    {
      id: 301551, productId: 18231, sku: 'NVT-AER14-7161',
      productTitle: 'Novatek Aero 14" Laptop, Core i7, 16GB RAM, 512GB SSD, Windows 11',
      unitPrice: 899, quantity: 1, lineTotal: 899,
    },
    {
      id: 301552, productId: mouse.id, sku: 'NVT-GLD-2410', productTitle: mouse.name,
      unitPrice: 19.99, quantity: 2, lineTotal: 39.98,
    },
  ],
  payments: [
    {
      id: 77120, method: 'CARD', status: 'FAILED', amount: 1016.45,
      providerRef: 'ch_3QfX81dA9', createdAt: '2026-10-24T18:42:10Z',
    },
    {
      id: 77121, method: 'CARD', status: 'PARTIALLY_REFUNDED', amount: 1016.45,
      providerRef: 'ch_3QfX8Q2kL', createdAt: '2026-10-24T18:44:31Z',
    },
  ],
  shipments: [
    {
      id: 64009, carrier: 'UPS', trackingNumber: '1Z9X42E80394817265', status: 'DELIVERED',
      shippedAt: '2026-10-26T09:15:00Z', deliveredAt: '2026-10-31T16:02:00Z',
    },
  ],
  returns: [
    {
      id: 15233, order, orderItemId: 301552, product: mouse, customer, quantity: 1,
      reason: 'DEFECTIVE', status: 'CLOSED',
      customerNote: 'Left click double-clicks on its own after a week of use.',
      sellerNote: 'Confirmed faulty switch on inspection. Refund approved.',
      openedAt: '2026-11-07T10:21:00Z', resolvedAt: '2026-11-14T13:40:00Z',
    },
  ],
  refunds: [
    {
      id: 20981, order, returnRequestId: 15233, amount: 21.64, reason: 'Return: defective item',
      status: 'ISSUED', issuedBy: { id: 31, name: 'Dana Whitfield' }, createdAt: '2026-11-14T13:42:00Z',
    },
  ],
};
