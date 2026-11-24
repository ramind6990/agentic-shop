import { Ref } from '../../shared/models/api.model';

/** Mirrors the backend Permission enum: what the signed-in user may open or call. */
export type Permission =
  | 'CATALOG_READ'
  | 'ORDERS_READ'
  | 'RETURNS_READ'
  | 'REFUNDS_READ'
  | 'CUSTOMERS_READ'
  | 'SELLERS_READ'
  | 'SELLER_FINANCE_READ'
  | 'USERS_READ'
  | 'TICKETS_READ'
  | 'TICKET_INTERNAL_READ'
  | 'FLAGS_READ'
  | 'DOCUMENTS_READ';

export interface LoginResponse {
  token: string;
  /** ISO instant. */
  expiresAt: string;
}

/** GET /api/auth/me: who is signed in and what they may do. */
export interface Session {
  id: number;
  username: string;
  fullName: string;
  email: string;
  role: string;
  /** Only set for SELLER users. */
  seller: Ref | null;
  permissions: Permission[];
}
