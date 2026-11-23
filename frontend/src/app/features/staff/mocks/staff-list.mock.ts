// Mock rows for the staff users list until GET /api/users is wired.
import { MOCK_SELLER_REFS, MOCK_STAFF_REFS, hoursAgo } from '../../../shared/mocks/reference.mock';
import { PageResponse } from '../../../shared/models/api.model';
import { StaffRole, StaffUser } from '../models/staff-user.model';

const s = MOCK_STAFF_REFS;
const rows: [number, string, StaffRole, number | null][] = [
  [1, 'Alex Morgan', 'ADMIN', null],
  [s.t1.id, s.t1.name, 'SUPPORT_T1', null],
  [s.t1b.id, s.t1b.name, 'SUPPORT_T1', null],
  [40, 'Hannah Becker', 'SUPPORT_T1', null],
  [s.t2.id, s.t2.name, 'SUPPORT_T2', null],
  [19, 'Grace Liu', 'SUPPORT_T2', null],
  [25, 'Daniel Price', 'FINANCE', null],
  [s.fraud.id, s.fraud.name, 'FRAUD', null],
  [601, 'Rachel Green', 'SELLER', 41],
  [602, 'Owen Carter', 'SELLER', 41],
  [655, 'Emma Lewis', 'SELLER', 266],
  [688, 'Jakob Weber', 'SELLER', 203],
];

const domain = 'agenticshop.example';

export const MOCK_STAFF_PAGE: PageResponse<StaffUser> = {
  page: 0,
  size: 25,
  totalElements: 612,
  totalPages: 25,
  content: rows.map(([id, fullName, role, sellerId], i) => {
    const seller = sellerId ? MOCK_SELLER_REFS.find((x) => x.id === sellerId) ?? null : null;
    const local = fullName.toLowerCase().replace(' ', '.');
    return {
      id,
      fullName,
      email: seller ? `${local}@${seller.name.toLowerCase().replace(/[^a-z]+/g, '')}.example` : `${local}@${domain}`,
      role,
      seller,
      status: 'ACTIVE',
      createdAt: hoursAgo(9000 - i * 500),
    };
  }),
};
