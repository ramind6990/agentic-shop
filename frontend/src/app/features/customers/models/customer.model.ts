// Customer shapes, mirroring identity/dto/UserDtos.java and CustomerReviewDto in the backend.
import { Address, Ref } from '../../../shared/models/api.model';

export type UserStatus = 'ACTIVE' | 'SUSPENDED';

/** Row in the customer list (GET /api/customers). */
export interface CustomerSummary {
  id: number;
  fullName: string;
  email: string;
  status: UserStatus;
  createdAt: string;
}

export interface CustomerAddress extends Address {
  id: number;
  /** e.g. Home, Work. */
  label: string;
  defaultAddress: boolean;
}

/** GET /api/customers/{id} */
export interface CustomerDetail extends CustomerSummary {
  phone: string;
  addresses: CustomerAddress[];
  stats: { orderCount: number; returnCount: number; ticketCount: number; activeFlagCount: number };
}

/** A review written by the customer, with the product it is about (GET /api/customers/{id}/reviews). */
export interface CustomerReview {
  id: number;
  rating: number;
  title: string;
  body: string;
  product: Ref;
  createdAt: string;
}
