// Support ticket shapes, mirroring support/dto/TicketDtos.java in the backend.
import { Ref } from '../../../shared/models/api.model';
import { OrderRef } from '../../returns/models/return.model';

export const TICKET_STATUSES = ['OPEN', 'PENDING', 'ESCALATED', 'RESOLVED', 'CLOSED'] as const;
export type TicketStatus = (typeof TICKET_STATUSES)[number];

export const TICKET_PRIORITIES = ['LOW', 'NORMAL', 'HIGH', 'URGENT'] as const;
export type TicketPriority = (typeof TICKET_PRIORITIES)[number];

export const TICKET_CATEGORIES = ['ORDER', 'RETURN', 'PAYMENT', 'SHIPPING', 'ACCOUNT', 'PRODUCT'] as const;
export type TicketCategory = (typeof TICKET_CATEGORIES)[number];

export type SupportTier = 'T1' | 'T2';
export type AuthorType = 'CUSTOMER' | 'STAFF' | 'SELLER' | 'SYSTEM';

/** Row in the ticket list (GET /api/tickets). */
export interface TicketSummary {
  id: number;
  ticketNumber: string;
  subject: string;
  category: TicketCategory;
  status: TicketStatus;
  priority: TicketPriority;
  tier: SupportTier;
  customer: Ref;
  order: OrderRef | null;
  assignee: Ref | null;
  createdAt: string;
  updatedAt: string;
}

/** GET /api/tickets/{id} */
export interface TicketDetail extends TicketSummary {
  seller: Ref | null;
  messageCount: number;
}

/** GET /api/tickets/{id}/messages (paged, oldest first). */
export interface TicketMessage {
  id: number;
  authorType: AuthorType;
  /** Null for SYSTEM messages. */
  author: Ref | null;
  body: string;
  /** Staff-only note, never shown to the customer or seller. */
  internal: boolean;
  /** ISO instant. */
  createdAt: string;
}
