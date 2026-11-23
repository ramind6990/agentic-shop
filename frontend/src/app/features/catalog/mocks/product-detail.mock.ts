// Mock data for the product detail screen until it is wired to /api/products/{id} and its review endpoints.
import { PageResponse } from '../../../shared/models/api.model';
import { ProductDetail, RatingSummary, Review } from '../models/product.model';

const image = (k: number) => `https://picsum.photos/seed/nvt-aer14-7161-${k}/800/800`;
const title = 'Novatek Aero 14" Laptop, Core i7, 16GB RAM, 512GB SSD, Windows 11';

export const MOCK_PRODUCT_DETAIL: ProductDetail = {
  id: 18231,
  sku: 'NVT-AER14-7161',
  title,
  slug: 'novatek-aero-14-laptop-core-i7-16gb',
  description:
    'The Novatek Aero 14 is a thin, light laptop built for long workdays away from a desk. ' +
    'A Core i7 processor and 16GB of RAM keep browsing, office apps and video calls smooth, ' +
    'and the 512GB SSD boots in seconds.\n\n' +
    '- Processor: Core i7\n- RAM: 16 GB\n- Storage: 512 GB SSD\n- Screen size: 14 in\n' +
    '- Operating system: Windows 11\n- Touchscreen: No\n- Warranty: 24 months\n\n' +
    'Keep vents clear and use the supplied charger. Covered by the manufacturer warranty against ' +
    'defects in materials and workmanship; accidental damage is not included.',
  price: 899,
  listPrice: 999,
  currency: 'USD',
  status: 'ACTIVE',
  ratingAvg: 4.3,
  ratingCount: 210,
  salesCount: 1840,
  createdAt: '2024-02-11T09:30:00Z',
  brand: { id: 3, name: 'Novatek' },
  seller: { id: 41, name: 'Maple Street Electronics' },
  breadcrumbs: [
    { id: 1, name: 'Electronics', slug: 'electronics' },
    { id: 12, name: 'Computers', slug: 'computers' },
    { id: 104, name: 'Laptops', slug: 'laptops' },
  ],
  images: [1, 2, 3, 4].map((k) => ({
    url: image(k),
    altText: k === 1 ? title : `${title} - view ${k}`,
  })),
  attributes: [
    { code: 'cpu', name: 'Processor', dataType: 'ENUM', unit: null, values: ['core_i7'], labels: ['Core i7'] },
    { code: 'ram_gb', name: 'RAM', dataType: 'NUMBER', unit: 'GB', values: ['16'], labels: ['16'] },
    { code: 'storage_gb', name: 'Storage', dataType: 'NUMBER', unit: 'GB', values: ['512'], labels: ['512'] },
    { code: 'screen_size_in', name: 'Screen size', dataType: 'NUMBER', unit: 'in', values: ['14'], labels: ['14'] },
    { code: 'os', name: 'Operating system', dataType: 'ENUM', unit: null, values: ['windows_11'], labels: ['Windows 11'] },
    {
      code: 'ports', name: 'Ports', dataType: 'MULTI_ENUM', unit: null,
      values: ['usb_c', 'usb_a', 'hdmi'], labels: ['USB-C', 'USB-A', 'HDMI'],
    },
    { code: 'touchscreen', name: 'Touchscreen', dataType: 'BOOLEAN', unit: null, values: ['false'], labels: ['No'] },
    { code: 'warranty_months', name: 'Warranty', dataType: 'NUMBER', unit: 'months', values: ['24'], labels: ['24'] },
  ],
  stock: { available: 42, inStock: true, warehouseCode: 'WH-EAST', restockEta: null },
};

export const MOCK_RATING_SUMMARY: RatingSummary = {
  average: 4.3,
  total: 210,
  distribution: { '5': 112, '4': 58, '3': 19, '2': 9, '1': 12 },
};

export const MOCK_REVIEW_PAGE: PageResponse<Review> = {
  page: 0,
  size: 5,
  totalElements: 210,
  totalPages: 42,
  content: [
    {
      id: 90412, rating: 5, title: 'Fast and quiet',
      body: 'Boots in seconds and the fan barely spins up during normal work. Screen is bright enough to use by a window.',
      helpfulCount: 34, authorName: 'Emily R.', verifiedPurchase: true, createdAt: '2026-10-28T14:12:00Z',
    },
    {
      id: 90377, rating: 4, title: 'Great for travel',
      body: 'Light enough to carry all day. Battery gets me through most of a workday, though not quite the advertised hours.',
      helpfulCount: 21, authorName: 'Tom H.', verifiedPurchase: true, createdAt: '2026-10-19T08:45:00Z',
    },
    {
      id: 90301, rating: 2, title: 'Battery drains in a few hours',
      body: 'Performance is fine but the battery barely lasts three hours with video calls. Expected more at this price.',
      helpfulCount: 17, authorName: 'Lukas M.', verifiedPurchase: true, createdAt: '2026-10-02T19:03:00Z',
    },
    {
      id: 90288, rating: 5, title: 'Exactly as described',
      body: 'Arrived well packed, two days early. Setup took ten minutes.',
      helpfulCount: 6, authorName: 'Priya S.', verifiedPurchase: false, createdAt: '2026-09-27T11:30:00Z',
    },
    {
      id: 90215, rating: 1, title: 'Keyboard stopped working',
      body: 'Several keys stopped responding after three weeks. Returned it for a refund.',
      helpfulCount: 12, authorName: 'Jordan K.', verifiedPurchase: true, createdAt: '2026-09-14T16:20:00Z',
    },
  ],
};
