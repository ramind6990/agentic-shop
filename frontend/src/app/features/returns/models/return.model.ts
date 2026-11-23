// Return and refund shapes, mirroring returns/dto/ReturnDtos.java in the backend.
import { Ref } from '../../../shared/models/api.model';

export const RETURN_STATUSES = ['REQUESTED', 'APPROVED', 'REJECTED', 'RECEIVED', 'DISPUTED', 'CLOSED'] as const;
export type ReturnStatus = (typeof RETURN_STATUSES)[number];

export const RETURN_REASONS = [
  'DAMAGED',
  'NOT_AS_DESCRIBED',
  'WRONG_ITEM',
  'DOES_NOT_FIT',
  'CHANGED_MIND',
  'ARRIVED_LATE',
  'DEFECTIVE',
] as const;
export type ReturnReason = (typeof RETURN_REASONS)[number];

export const REFUND_STATUSES = ['PENDING', 'ISSUED', 'FAILED'] as const;
export type RefundStatus = (typeof REFUND_STATUSES)[number];

export interface OrderRef {
  id: number;
  orderNumber: string;
}

export interface ReturnRequest {
  id: number;
  order: OrderRef;
  orderItemId: number;
  product: Ref;
  customer: Ref;
  quantity: number;
  reason: ReturnReason;
  status: ReturnStatus;
  customerNote: string | null;
  sellerNote: string | null;
  /** ISO instant. */
  openedAt: string;
  /** ISO instant, null while open. */
  resolvedAt: string | null;
}

export interface Refund {
  id: number;
  order: OrderRef;
  /** Set when the refund came from a return. */
  returnRequestId: number | null;
  amount: number;
  reason: string;
  status: RefundStatus;
  /** Staff user who issued it (Tier-1 up to $100, Tier-2 above). */
  issuedBy: Ref | null;
  /** ISO instant. */
  createdAt: string;
}
