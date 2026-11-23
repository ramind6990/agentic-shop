// Mock data for the product search screen until it is wired to /api/products and /api/products/facets.
// Scenario: browsing Electronics > Computers > Laptops, sorted by relevance, first page.
import { PageResponse } from '../../../shared/models/api.model';
import { ProductFacets, ProductSummary } from '../models/product.model';

const laptops = { id: 104, name: 'Laptops' };
const img = (sku: string) => `https://picsum.photos/seed/${sku.toLowerCase()}-1/400/400`;

export const MOCK_PRODUCT_PAGE: PageResponse<ProductSummary> = {
  page: 0,
  size: 12,
  totalElements: 1204,
  totalPages: 101,
  content: [
    {
      id: 18231, sku: 'NVT-AER14-7161', slug: 'novatek-aero-14-laptop-core-i7-16gb',
      title: 'Novatek Aero 14" Laptop, Core i7, 16GB RAM, 512GB SSD, Windows 11',
      price: 899, listPrice: 999, currency: 'USD', ratingAvg: 4.3, ratingCount: 210, salesCount: 1840,
      brand: { id: 3, name: 'Novatek' }, seller: { id: 41, name: 'Maple Street Electronics' },
      category: laptops, thumbnailUrl: img('NVT-AER14-7161'), inStock: true,
    },
    {
      id: 18244, sku: 'ZTR-PRO16-3264', slug: 'zentro-pro-16-laptop-ryzen-9-32gb',
      title: 'Zentro Pro 16" Laptop, Ryzen 9, 32GB RAM, 1TB SSD, Windows 11 Pro',
      price: 1649, listPrice: null, currency: 'USD', ratingAvg: 4.6, ratingCount: 88, salesCount: 512,
      brand: { id: 7, name: 'Zentro' }, seller: { id: 112, name: 'Harbor Tech Supply' },
      category: laptops, thumbnailUrl: img('ZTR-PRO16-3264'), inStock: true,
    },
    {
      id: 18302, sku: 'LUM-AIR13-0825', slug: 'lumio-air-13-laptop-core-i5-8gb',
      title: 'Lumio Air 13" Laptop, Core i5, 8GB RAM, 256GB SSD, ChromeOS',
      price: 449, listPrice: 529, currency: 'USD', ratingAvg: 3.9, ratingCount: 41, salesCount: 233,
      brand: { id: 12, name: 'Lumio' }, seller: { id: 41, name: 'Maple Street Electronics' },
      category: laptops, thumbnailUrl: img('LUM-AIR13-0825'), inStock: true,
    },
    {
      id: 18377, sku: 'NVT-FORGE17-6420', slug: 'novatek-forge-17-gaming-laptop',
      title: 'Novatek Forge 17" Gaming Laptop, Core i9, 64GB RAM, 2TB SSD, RTX graphics',
      price: 2399, listPrice: null, currency: 'USD', ratingAvg: 4.8, ratingCount: 132, salesCount: 406,
      brand: { id: 3, name: 'Novatek' }, seller: { id: 87, name: 'Pixel Peak Computers' },
      category: laptops, thumbnailUrl: img('NVT-FORGE17-6420'), inStock: false,
    },
    {
      id: 18410, sku: 'KRS-BOOK15-1651', slug: 'kestrel-book-15-laptop-ryzen-7-16gb',
      title: 'Kestrel Book 15" Laptop, Ryzen 7, 16GB RAM, 512GB SSD, Windows 11',
      price: 749, listPrice: 849, currency: 'USD', ratingAvg: 4.1, ratingCount: 302, salesCount: 2210,
      brand: { id: 19, name: 'Kestrel' }, seller: { id: 112, name: 'Harbor Tech Supply' },
      category: laptops, thumbnailUrl: img('KRS-BOOK15-1651'), inStock: true,
    },
    {
      id: 18455, sku: 'ZTR-FLIP14-1625', slug: 'zentro-flip-14-2-in-1-laptop',
      title: 'Zentro Flip 14" 2-in-1 Laptop, Core i7, 16GB RAM, 512GB SSD, Touchscreen',
      price: 1099, listPrice: null, currency: 'USD', ratingAvg: 4.4, ratingCount: 67, salesCount: 380,
      brand: { id: 7, name: 'Zentro' }, seller: { id: 203, name: 'Northgate Digital' },
      category: laptops, thumbnailUrl: img('ZTR-FLIP14-1625'), inStock: true,
    },
    {
      id: 18501, sku: 'LUM-STUDIO16-3210', slug: 'lumio-studio-16-creator-laptop',
      title: 'Lumio Studio 16" Creator Laptop, Core Ultra 7, 32GB RAM, 1TB SSD, OLED',
      price: 1899, listPrice: 2099, currency: 'USD', ratingAvg: 4.5, ratingCount: 54, salesCount: 190,
      brand: { id: 12, name: 'Lumio' }, seller: { id: 87, name: 'Pixel Peak Computers' },
      category: laptops, thumbnailUrl: img('LUM-STUDIO16-3210'), inStock: true,
    },
    {
      id: 18533, sku: 'KRS-LITE14-0812', slug: 'kestrel-lite-14-laptop-celeron',
      title: 'Kestrel Lite 14" Laptop, Celeron, 8GB RAM, 128GB eMMC, Windows 11 S',
      price: 279, listPrice: null, currency: 'USD', ratingAvg: 2.8, ratingCount: 96, salesCount: 701,
      brand: { id: 19, name: 'Kestrel' }, seller: { id: 203, name: 'Northgate Digital' },
      category: laptops, thumbnailUrl: img('KRS-LITE14-0812'), inStock: true,
    },
    {
      id: 18590, sku: 'NVT-AER13-1610', slug: 'novatek-aero-13-laptop-core-i5-16gb',
      title: 'Novatek Aero 13" Laptop, Core i5, 16GB RAM, 1TB SSD, Windows 11',
      price: 799, listPrice: null, currency: 'USD', ratingAvg: 4.2, ratingCount: 158, salesCount: 960,
      brand: { id: 3, name: 'Novatek' }, seller: { id: 41, name: 'Maple Street Electronics' },
      category: laptops, thumbnailUrl: img('NVT-AER13-1610'), inStock: true,
    },
    {
      id: 18624, sku: 'ZTR-WORK15-1651', slug: 'zentro-work-15-business-laptop',
      title: 'Zentro Work 15" Business Laptop, Core i7, 16GB RAM, 512GB SSD, Windows 11 Pro',
      price: 1199, listPrice: 1299, currency: 'USD', ratingAvg: 4.0, ratingCount: 73, salesCount: 341,
      brand: { id: 7, name: 'Zentro' }, seller: { id: 112, name: 'Harbor Tech Supply' },
      category: laptops, thumbnailUrl: img('ZTR-WORK15-1651'), inStock: false,
    },
    {
      id: 18671, sku: 'LUM-AIR15-1651', slug: 'lumio-air-15-laptop-ryzen-5-16gb',
      title: 'Lumio Air 15" Laptop, Ryzen 5, 16GB RAM, 512GB SSD, Windows 11',
      price: 629, listPrice: null, currency: 'USD', ratingAvg: 4.1, ratingCount: 119, salesCount: 815,
      brand: { id: 12, name: 'Lumio' }, seller: { id: 41, name: 'Maple Street Electronics' },
      category: laptops, thumbnailUrl: img('LUM-AIR15-1651'), inStock: true,
    },
    {
      id: 18702, sku: 'KRS-PRO16-3220', slug: 'kestrel-pro-16-laptop-core-i9-32gb',
      title: 'Kestrel Pro 16" Laptop, Core i9, 32GB RAM, 2TB SSD, Windows 11 Pro',
      price: 1999, listPrice: null, currency: 'USD', ratingAvg: 4.7, ratingCount: 29, salesCount: 98,
      brand: { id: 19, name: 'Kestrel' }, seller: { id: 87, name: 'Pixel Peak Computers' },
      category: laptops, thumbnailUrl: img('KRS-PRO16-3220'), inStock: true,
    },
  ],
};

export const MOCK_PRODUCT_FACETS: ProductFacets = {
  total: 1204,
  categories: [
    { id: 104, name: 'Laptops', slug: 'laptops', count: 1204 },
    { id: 105, name: 'Desktops', slug: 'desktops', count: 486 },
    { id: 106, name: 'Monitors', slug: 'monitors', count: 731 },
    { id: 107, name: 'Tablets', slug: 'tablets', count: 392 },
  ],
  brands: [
    { value: '3', label: 'Novatek', count: 312 },
    { value: '7', label: 'Zentro', count: 287 },
    { value: '12', label: 'Lumio', count: 251 },
    { value: '19', label: 'Kestrel', count: 204 },
    { value: '23', label: 'Arcwave', count: 150 },
  ],
  price: { min: 249, max: 3499 },
  ratings: [
    { value: '4', label: '4★ & up', count: 702 },
    { value: '3', label: '3★ & up', count: 1033 },
    { value: '2', label: '2★ & up', count: 1161 },
    { value: '1', label: '1★ & up', count: 1204 },
  ],
  attributes: [
    {
      code: 'ram_gb', name: 'RAM', dataType: 'NUMBER', unit: 'GB', range: { min: 8, max: 64 },
      values: [
        { value: '8', label: '8 GB', count: 268 },
        { value: '16', label: '16 GB', count: 541 },
        { value: '32', label: '32 GB', count: 301 },
        { value: '64', label: '64 GB', count: 94 },
      ],
    },
    {
      code: 'screen_size_in', name: 'Screen size', dataType: 'NUMBER', unit: 'in',
      range: { min: 13, max: 17.3 }, values: [],
    },
    {
      code: 'cpu', name: 'Processor', dataType: 'ENUM', unit: null, range: null,
      values: [
        { value: 'core_i5', label: 'Core i5', count: 233 },
        { value: 'core_i7', label: 'Core i7', count: 389 },
        { value: 'core_i9', label: 'Core i9', count: 121 },
        { value: 'ryzen_7', label: 'Ryzen 7', count: 210 },
        { value: 'ryzen_9', label: 'Ryzen 9', count: 96 },
      ],
    },
    {
      code: 'os', name: 'Operating system', dataType: 'ENUM', unit: null, range: null,
      values: [
        { value: 'windows_11', label: 'Windows 11', count: 802 },
        { value: 'windows_11_pro', label: 'Windows 11 Pro', count: 244 },
        { value: 'chromeos', label: 'ChromeOS', count: 158 },
      ],
    },
    {
      code: 'touchscreen', name: 'Touchscreen', dataType: 'BOOLEAN', unit: null, range: null,
      values: [
        { value: 'true', label: 'Yes', count: 317 },
        { value: 'false', label: 'No', count: 887 },
      ],
    },
  ],
};

/** Breadcrumb of the category being browsed. */
export const MOCK_CATEGORY_PATH = ['Electronics', 'Computers', 'Laptops'];
