// Order shapes, mirroring order/dto/OrderDtos.java in the backend.
import { Address, Ref } from '../../../shared/models/api.model';
import { Refund, ReturnRequest } from '../../returns/models/return.model';

/** Values of the backend OrderStatus enum (also served by /api/meta/enums). */
export const ORDER_STATUSES = [
  'PENDING',
  'PAID',
  'SHIPPED',
  'DELIVERED',
  'CANCELLED',
  'RETURNED',
  'REFUNDED',
  'PARTIALLY_REFUNDED',
] as const;

export type OrderStatus = (typeof ORDER_STATUSES)[number];

/** Row in the order list (GET /api/orders). */
export interface OrderSummary {
  id: number;
  orderNumber: string;
  status: OrderStatus;
  /** ISO instant. */
  placedAt: string;
  total: number;
  currency: string;
  customer: Ref;
  seller: Ref;
  itemCount: number;
}

// ---------- detail (GET /api/orders/{idOrNumber}) ----------

export type PaymentMethod = 'CARD' | 'PAYPAL' | 'GIFT_CARD' | 'STORE_CREDIT';
export type PaymentStatus =
  | 'AUTHORIZED'
  | 'CAPTURED'
  | 'FAILED'
  | 'REFUNDED'
  | 'PARTIALLY_REFUNDED'
  | 'VOIDED';
export type Carrier = 'UPS' | 'FEDEX' | 'DHL' | 'USPS';
export type ShipmentStatus = 'LABEL_CREATED' | 'IN_TRANSIT' | 'DELIVERED' | 'LOST' | 'RETURNED';

export interface OrderItem {
  id: number;
  productId: number;
  /** Title, SKU and price are copied onto the line when the order is placed. */
  productTitle: string;
  sku: string;
  unitPrice: number;
  quantity: number;
  lineTotal: number;
}

export interface Payment {
  id: number;
  method: PaymentMethod;
  status: PaymentStatus;
  amount: number;
  providerRef: string;
  /** ISO instant. */
  createdAt: string;
}

export interface Shipment {
  id: number;
  carrier: Carrier;
  trackingNumber: string;
  status: ShipmentStatus;
  shippedAt: string | null;
  deliveredAt: string | null;
}

export interface OrderDetail {
  id: number;
  orderNumber: string;
  status: OrderStatus;
  placedAt: string;
  customer: Ref;
  seller: Ref;
  /** Snapshot of the address at order time. */
  shippingAddress: Address;
  subtotal: number;
  shippingFee: number;
  tax: number;
  total: number;
  currency: string;
  items: OrderItem[];
  payments: Payment[];
  shipments: Shipment[];
  returns: ReturnRequest[];
  refunds: Refund[];
}

/** Query parameters of GET /api/orders. `from`/`to` are ISO instants. */
export interface OrderFilter {
  q?: string;
  status?: OrderStatus;
  customerId?: number;
  sellerId?: number;
  from?: string;
  to?: string;
}
