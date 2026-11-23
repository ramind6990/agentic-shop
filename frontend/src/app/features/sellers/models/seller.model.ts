// Seller (tenant) shapes, mirroring seller/dto/SellerDtos.java in the backend.

export type SellerStatus = 'ACTIVE' | 'SUSPENDED' | 'OFFBOARDED';
export type FeePlan = 'STANDARD' | 'PRO' | 'ENTERPRISE';
export type PayoutStatus = 'PENDING' | 'PAID' | 'ON_HOLD';

/** Row in the seller list (GET /api/sellers). */
export interface SellerSummary {
  id: number;
  name: string;
  slug: string;
  /** ISO country code. */
  country: string;
  ratingAvg: number;
  status: SellerStatus;
  joinedAt: string;
}

/** GET /api/sellers/{idOrSlug} */
export interface SellerDetail extends SellerSummary {
  legalName: string;
  description: string;
  email: string;
  currentFeePlan: FeePlan;
  stats: { productCount: number; orderCount: number };
}

/** Fee agreement in force between two dates (GET /api/sellers/{id}/contracts). */
export interface SellerContract {
  id: number;
  feePlan: FeePlan;
  /** Fraction, e.g. 0.12 for 12%. */
  commissionRate: number;
  perOrderFee: number;
  monthlyFee: number;
  returnWindowDays: number;
  /** ISO date, inclusive. */
  effectiveFrom: string;
  /** ISO date, exclusive; null for the current contract. */
  effectiveTo: string | null;
  /** Code of the contract document (see /api/documents/{code}). */
  documentCode: string;
}

/** Bi-weekly payout: net = gross − commission − fees − refunds (GET /api/sellers/{id}/payouts). */
export interface Payout {
  id: number;
  periodStart: string;
  periodEnd: string;
  orderCount: number;
  grossSales: number;
  commissionAmount: number;
  feesAmount: number;
  refundsAmount: number;
  netAmount: number;
  status: PayoutStatus;
  paidAt: string | null;
}
