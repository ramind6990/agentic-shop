// Mock data for the customer detail screen until it is wired to /api/customers/{id} and its sub-resources.
import { CustomerDetail, CustomerReview } from '../models/customer.model';

export const MOCK_CUSTOMER: CustomerDetail = {
  id: 3310,
  fullName: 'Jordan King',
  email: 'jordan.king@example.com',
  phone: '+1 (775) 555-0148',
  status: 'ACTIVE',
  createdAt: '2024-03-17T16:45:00Z',
  addresses: [
    {
      id: 5501, label: 'Home', recipient: 'Jordan King', line1: '880 Sierra Vista Dr', line2: null,
      city: 'Reno', region: 'NV', postalCode: '89503', country: 'US', defaultAddress: true,
    },
    {
      id: 5502, label: 'Work', recipient: 'Jordan King', line1: '1 E Liberty St', line2: 'Suite 600',
      city: 'Reno', region: 'NV', postalCode: '89501', country: 'US', defaultAddress: false,
    },
  ],
  stats: { orderCount: 23, returnCount: 4, ticketCount: 4, activeFlagCount: 1 },
};

export const MOCK_CUSTOMER_REVIEWS: CustomerReview[] = [
  {
    id: 90215, rating: 1, title: 'Keyboard stopped working',
    body: 'Several keys stopped responding after three weeks. Returned it for a refund.',
    product: { id: 18533, name: 'Kestrel Lite 14" Laptop, Celeron, 8GB RAM, 128GB eMMC, Windows 11 S' },
    createdAt: '2026-09-14T16:20:00Z',
  },
  {
    id: 88102, rating: 4, title: 'Warm and packs small',
    body: 'Kept me warm down to about 35°F. Stuff sack is a bit tight.',
    product: { id: 30211, name: 'Trailhead Ridge 20°F Mummy Sleeping Bag, Synthetic Fill, Regular' },
    createdAt: '2026-06-03T07:55:00Z',
  },
  {
    id: 86440, rating: 5, title: 'Sturdy tent',
    body: 'Survived a windy night on the Tahoe Rim Trail without a problem. Easy to pitch alone.',
    product: { id: 30108, name: 'Trailhead Basecamp 3-Person Dome Tent, Aluminum Poles, Green' },
    createdAt: '2026-05-21T19:10:00Z',
  },
];
