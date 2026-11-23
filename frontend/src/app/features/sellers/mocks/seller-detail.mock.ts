// Mock data for the seller detail screen until it is wired to /api/sellers/{idOrSlug} and its sub-resources.
import { Payout, SellerContract, SellerDetail } from '../models/seller.model';

export const MOCK_SELLER: SellerDetail = {
  id: 41,
  name: 'Maple Street Electronics',
  slug: 'maple-street-electronics',
  legalName: 'Maple Street Electronics LLC',
  description:
    'Independent electronics retailer from Portland, Oregon selling laptops, accessories and home audio since 2016.',
  email: 'orders@maplestreet-electronics.example',
  country: 'US',
  ratingAvg: 4.4,
  status: 'ACTIVE',
  joinedAt: '2023-12-04T10:00:00Z',
  currentFeePlan: 'PRO',
  stats: { productCount: 812, orderCount: 4210 },
};

/** Newest first: the 2025-01-01 renewal followed the seller fee schedule change. */
export const MOCK_SELLER_CONTRACTS: SellerContract[] = [
  {
    id: 1093, feePlan: 'PRO', commissionRate: 0.11, perOrderFee: 0.25, monthlyFee: 39.99,
    returnWindowDays: 30, effectiveFrom: '2025-01-01', effectiveTo: null, documentCode: 'contract-maple-street-electronics',
  },
  {
    id: 588, feePlan: 'PRO', commissionRate: 0.1, perOrderFee: 0.3, monthlyFee: 39.99,
    returnWindowDays: 30, effectiveFrom: '2024-06-01', effectiveTo: '2025-01-01', documentCode: 'contract-maple-street-electronics',
  },
  {
    id: 41, feePlan: 'STANDARD', commissionRate: 0.15, perOrderFee: 0.3, monthlyFee: 0,
    returnWindowDays: 30, effectiveFrom: '2023-12-04', effectiveTo: '2024-06-01', documentCode: 'contract-maple-street-electronics',
  },
];

const periods: [string, string, number, number, number][] = [
  // start, end, orders, gross, refunds
  ['2026-11-09', '2026-11-22', 63, 18430.5, 412.8],
  ['2026-10-26', '2026-11-08', 58, 16955.2, 1016.45],
  ['2026-10-12', '2026-10-25', 51, 14120.0, 89.99],
  ['2026-09-28', '2026-10-11', 49, 13877.4, 0],
  ['2026-09-14', '2026-09-27', 47, 12904.15, 359.0],
  ['2026-08-31', '2026-09-13', 55, 15210.6, 64.2],
  ['2026-08-17', '2026-08-30', 61, 17302.9, 1180.0],
  ['2026-08-03', '2026-08-16', 44, 11688.35, 0],
];

/** Payouts computed with the current contract: 11% commission, $0.25 per order, $39.99 monthly fee split per period. */
export const MOCK_SELLER_PAYOUTS: Payout[] = periods.map(([start, end, orders, gross, refunds], i) => {
  const commission = Math.round(gross * 0.11 * 100) / 100;
  const fees = Math.round((orders * 0.25 + 20) * 100) / 100;
  const net = Math.round((gross - commission - fees - refunds) * 100) / 100;
  const current = i === 0;
  return {
    id: 9100 - i,
    periodStart: start,
    periodEnd: end,
    orderCount: orders,
    grossSales: gross,
    commissionAmount: commission,
    feesAmount: fees,
    refundsAmount: refunds,
    netAmount: net,
    status: current ? 'PENDING' : 'PAID',
    paidAt: current ? null : `${end}T12:00:00Z`,
  };
});
