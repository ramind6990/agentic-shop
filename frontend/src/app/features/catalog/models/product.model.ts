// Catalog shapes, mirroring catalog/dto/ProductDtos.java in the backend.
import { Ref } from '../../../shared/models/api.model';
import { CategoryRef } from './category.model';

export type AttributeDataType = 'ENUM' | 'MULTI_ENUM' | 'NUMBER' | 'BOOLEAN';

export type ProductSort = 'relevance' | 'bestselling' | 'price_asc' | 'price_desc' | 'newest' | 'rating';

/** Card in a product listing (GET /api/products). */
export interface ProductSummary {
  id: number;
  sku: string;
  title: string;
  slug: string;
  price: number;
  listPrice: number | null;
  currency: string;
  ratingAvg: number;
  ratingCount: number;
  salesCount: number;
  brand: Ref;
  seller: Ref;
  category: Ref;
  thumbnailUrl: string;
  inStock: boolean;
}

// ---------- detail (GET /api/products/{id}) ----------

export type ProductStatus = 'ACTIVE' | 'DRAFT' | 'DELISTED';

export interface ProductImage {
  url: string;
  altText: string;
}

/** One attribute value ready for display. MULTI_ENUM has several labels. */
export interface ProductAttribute {
  code: string;
  name: string;
  dataType: AttributeDataType;
  unit: string | null;
  values: string[];
  labels: string[];
}

export interface Stock {
  available: number;
  inStock: boolean;
  warehouseCode: string;
  /** ISO date, set when out of stock and a restock is planned. */
  restockEta: string | null;
}

export interface ProductDetail {
  id: number;
  sku: string;
  title: string;
  slug: string;
  /** Plain text: intro paragraph, "- Name: value" spec lines, then care/warranty text. */
  description: string;
  price: number;
  listPrice: number | null;
  currency: string;
  status: ProductStatus;
  ratingAvg: number;
  ratingCount: number;
  salesCount: number;
  /** ISO instant. */
  createdAt: string;
  brand: Ref;
  seller: Ref;
  breadcrumbs: CategoryRef[];
  images: ProductImage[];
  attributes: ProductAttribute[];
  stock: Stock;
}

// ---------- reviews (GET /api/products/{id}/reviews and /reviews/summary) ----------

export type ReviewSort = 'newest' | 'helpful';

export interface Review {
  id: number;
  rating: number;
  title: string;
  body: string;
  helpfulCount: number;
  authorName: string;
  verifiedPurchase: boolean;
  /** ISO instant. */
  createdAt: string;
}

export interface RatingSummary {
  average: number;
  total: number;
  /** Review count per star, keyed "5" down to "1". */
  distribution: Record<string, number>;
}

// ---------- facets (GET /api/products/facets) ----------

export interface FacetValue {
  value: string;
  label: string;
  count: number;
}

export interface CategoryFacet {
  id: number;
  name: string;
  slug: string;
  count: number;
}

export interface RangeFacet {
  min: number;
  max: number;
}

export interface AttributeFacet {
  code: string;
  name: string;
  dataType: AttributeDataType;
  unit: string | null;
  values: FacetValue[];
  range: RangeFacet | null;
}

export interface ProductFacets {
  total: number;
  categories: CategoryFacet[];
  brands: FacetValue[];
  price: RangeFacet;
  ratings: FacetValue[];
  attributes: AttributeFacet[];
}
