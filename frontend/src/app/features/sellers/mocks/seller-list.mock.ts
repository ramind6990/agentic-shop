// Mock rows for the seller list until GET /api/sellers is wired. Sellers are named after what they sell.
import { hoursAgo } from '../../../shared/mocks/reference.mock';
import { PageResponse } from '../../../shared/models/api.model';
import { SellerStatus, SellerSummary } from '../models/seller.model';

const rows: [number, string, string, number, SellerStatus][] = [
  [41, 'Maple Street Electronics', 'US', 4.4, 'ACTIVE'],
  [112, 'Harbor Tech Supply', 'US', 4.2, 'ACTIVE'],
  [58, 'Maple Street Apparel Outlet', 'US', 4.0, 'ACTIVE'],
  [203, 'Northgate Digital', 'GB', 4.5, 'ACTIVE'],
  [317, 'Copper Kettle Kitchenware', 'CA', 4.6, 'ACTIVE'],
  [266, 'Trailhead Outdoor Co.', 'US', 4.3, 'ACTIVE'],
  [129, 'Bramble & Thread Home Textiles', 'GB', 3.9, 'ACTIVE'],
  [341, 'Polar Peak Sports', 'CA', 3.1, 'SUSPENDED'],
  [77, 'Lindenhof Kitchen Goods', 'DE', 4.4, 'ACTIVE'],
  [402, 'Sunset Bay Beauty', 'US', 3.7, 'ACTIVE'],
  [455, 'Rheinpark Audio', 'DE', 4.1, 'ACTIVE'],
  [18, 'Cedar Lane Toys', 'US', 2.9, 'OFFBOARDED'],
  [288, 'Bluewater Pet Supply', 'CA', 4.2, 'ACTIVE'],
  [366, 'Granite Garden & Patio', 'US', 4.0, 'ACTIVE'],
];

const slug = (name: string) => name.toLowerCase().replace(/&/g, 'and').replace(/[^a-z0-9]+/g, '-').replace(/-$/, '');

export const MOCK_SELLER_PAGE: PageResponse<SellerSummary> = {
  page: 0,
  size: 25,
  totalElements: 500,
  totalPages: 20,
  content: rows.map(([id, name, country, ratingAvg, status], i) => ({
    id,
    name,
    slug: slug(name),
    country,
    ratingAvg,
    status,
    joinedAt: hoursAgo(2000 + i * 1300),
  })),
};
