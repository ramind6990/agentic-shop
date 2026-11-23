// Mock account flags until /api/flags is wired.
import { MOCK_STAFF_REFS, hoursAgo } from '../../../shared/mocks/reference.mock';
import { PageResponse } from '../../../shared/models/api.model';
import { AccountFlag } from '../models/flag.model';

const fraudAnalyst = MOCK_STAFF_REFS.fraud;

export const MOCK_FLAGS: AccountFlag[] = [
  {
    id: 7012, subjectType: 'CUSTOMER', subjectId: 3310, flagType: 'REFUND_ABUSE', riskScore: 72,
    note: 'Three lost-parcel refunds in 2026, all on orders above $250.',
    createdBy: fraudAnalyst, active: true, createdAt: '2026-11-18T09:12:00Z',
  },
  {
    id: 6511, subjectType: 'CUSTOMER', subjectId: 3310, flagType: 'CHARGEBACK', riskScore: 55,
    note: 'Card chargeback filed after a lost-parcel refund had already been issued.',
    createdBy: fraudAnalyst, active: false, createdAt: '2026-04-02T14:30:00Z',
  },
  {
    id: 5820, subjectType: 'SELLER', subjectId: 41, flagType: 'POLICY_VIOLATION', riskScore: 30,
    note: 'Return rate on refurbished laptops above the category threshold for two months.',
    createdBy: fraudAnalyst, active: false, createdAt: '2025-09-15T11:05:00Z',
  },
  {
    id: 7044, subjectType: 'CUSTOMER', subjectId: 12007, flagType: 'ACCOUNT_TAKEOVER', riskScore: 88,
    note: 'Password and email changed from a new country, then two high-value orders within an hour.',
    createdBy: null, active: true, createdAt: hoursAgo(30),
  },
  {
    id: 7031, subjectType: 'SELLER', subjectId: 341, flagType: 'FAKE_REVIEWS', riskScore: 81,
    note: '42 five-star reviews in 3 days from accounts created the same week.',
    createdBy: fraudAnalyst, active: true, createdAt: hoursAgo(70),
  },
  {
    id: 6990, subjectType: 'CUSTOMER', subjectId: 4588, flagType: 'REFUND_ABUSE', riskScore: 64,
    note: '5 returns in 90 days, all marked "not as described", items received used.',
    createdBy: fraudAnalyst, active: true, createdAt: hoursAgo(140),
  },
  {
    id: 6902, subjectType: 'SELLER', subjectId: 18, flagType: 'POLICY_VIOLATION', riskScore: 77,
    note: 'Return rate of 23% over the last quarter; listings did not match items shipped.',
    createdBy: fraudAnalyst, active: false, createdAt: hoursAgo(900),
  },
  {
    id: 6855, subjectType: 'CUSTOMER', subjectId: 9142, flagType: 'CHARGEBACK', riskScore: 49,
    note: 'Chargeback on an order already refunded for a lost parcel.',
    createdBy: fraudAnalyst, active: true, createdAt: hoursAgo(1300),
  },
];

export const flagsFor = (subjectType: AccountFlag['subjectType'], subjectId: number) =>
  MOCK_FLAGS.filter((f) => f.subjectType === subjectType && f.subjectId === subjectId);

/** All flags, newest first. */
export const MOCK_FLAG_PAGE: PageResponse<AccountFlag> = {
  page: 0,
  size: 25,
  totalElements: 1046,
  totalPages: 42,
  content: [...MOCK_FLAGS].sort((a, b) => b.createdAt.localeCompare(a.createdAt)),
};
