// Mock rows for the customer list until GET /api/customers is wired.
import { MOCK_CUSTOMER_REFS, hoursAgo } from '../../../shared/mocks/reference.mock';
import { PageResponse } from '../../../shared/models/api.model';
import { CustomerSummary } from '../models/customer.model';

const extra = [
  'Sofia Rossi', 'Liam O\'Connor', 'Mia Wagner', 'Ethan Clarke', 'Charlotte Dubois', 'Henry Walsh',
  'Amelia Patel', 'Felix Braun', 'Grace Thompson', 'Lucas Martin',
];

const names = [...MOCK_CUSTOMER_REFS.map((c) => ({ id: c.id, name: c.name })), ...extra.map((name, i) => ({ id: 19000 + i * 37, name }))];

const email = (name: string) =>
  name
    .normalize('NFD')
    .replace(/[̀-ͯ']/g, '')
    .toLowerCase()
    .replace(' ', '.') + '@example.com';

export const MOCK_CUSTOMER_PAGE: PageResponse<CustomerSummary> = {
  page: 0,
  size: 25,
  totalElements: 20000,
  totalPages: 800,
  content: names.map(({ id, name }, i) => ({
    id,
    fullName: name,
    email: email(name),
    status: i === 6 ? 'SUSPENDED' : 'ACTIVE',
    createdAt: hoursAgo(400 + i * 1500),
  })),
};
