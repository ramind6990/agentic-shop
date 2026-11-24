// Product search state as it lives in the URL. The URL uses the same parameter names as GET /api/products,
// so a search URL can be read as an API call: /products?categoryId=104&brandIds=3&attr.ram_gb=16,32&page=1
import { ParamMap } from '@angular/router';

import { ParamValue } from '../../../core/http/http-params';
import { enumParam, intParam, listParam, numberParam } from '../../../shared/data/url-query';
import { ProductSort, ReviewSort } from './product.model';

export const PRODUCT_PAGE_SIZE = 24;
export const REVIEW_PAGE_SIZE = 10;

export const PRODUCT_SORTS: readonly ProductSort[] = [
  'relevance',
  'bestselling',
  'price_asc',
  'price_desc',
  'newest',
  'rating',
];
export const REVIEW_SORTS: readonly ReviewSort[] = ['newest', 'helpful'];

const ATTR_PREFIX = 'attr.';

export interface NumberRange {
  min: number | null;
  max: number | null;
}

export interface ProductQuery {
  q: string;
  categoryId: number | null;
  /** Limits the search to one seller (the seller's products tab). */
  sellerId: number | null;
  brandIds: number[];
  minPrice: number | null;
  maxPrice: number | null;
  minRating: number | null;
  inStock: boolean;
  /** Selected values per attribute code (`attr.<code>=v1,v2`). */
  attrs: Record<string, string[]>;
  /** Number ranges per attribute code (`attr.<code>.min` / `.max`). */
  ranges: Record<string, NumberRange>;
  sort: ProductSort;
  /** 0-based. */
  page: number;
}

/** Reads a product query from URL query parameters. Unknown or invalid values fall back to defaults. */
export function parseProductQuery(params: ParamMap): ProductQuery {
  const attrs: Record<string, string[]> = {};
  const ranges: Record<string, NumberRange> = {};
  for (const key of [...params.keys].sort()) {
    if (!key.startsWith(ATTR_PREFIX)) {
      continue;
    }
    const rest = key.slice(ATTR_PREFIX.length);
    const bound = rest.endsWith('.min') ? 'min' : rest.endsWith('.max') ? 'max' : null;
    if (bound) {
      const code = rest.slice(0, -4);
      const value = numberParam(params, key);
      if (code && value !== null) {
        ranges[code] = { ...(ranges[code] ?? { min: null, max: null }), [bound]: value };
      }
    } else {
      const values = listParam(params, key);
      if (rest && values.length > 0) {
        attrs[rest] = values;
      }
    }
  }

  return {
    q: params.get('q')?.trim() ?? '',
    categoryId: intParam(params, 'categoryId'),
    sellerId: intParam(params, 'sellerId'),
    brandIds: listParam(params, 'brandIds')
      .map(Number)
      .filter((id) => Number.isInteger(id)),
    minPrice: numberParam(params, 'minPrice'),
    maxPrice: numberParam(params, 'maxPrice'),
    minRating: numberParam(params, 'minRating'),
    inStock: params.get('inStock') === 'true',
    attrs,
    ranges,
    sort: enumParam(params, 'sort', PRODUCT_SORTS, 'relevance'),
    page: intParam(params, 'page') ?? 0,
  };
}

/** A search with no filters: page 0, default sort. */
export function emptyProductQuery(): ProductQuery {
  return {
    q: '',
    categoryId: null,
    sellerId: null,
    brandIds: [],
    minPrice: null,
    maxPrice: null,
    minRating: null,
    inStock: false,
    attrs: {},
    ranges: {},
    sort: 'relevance',
    page: 0,
  };
}

/** Filter parameters shared by /api/products and /api/products/facets (no sort or paging). */
export function productFilterParams(query: ProductQuery): Record<string, ParamValue> {
  const params: Record<string, ParamValue> = {
    q: query.q,
    categoryId: query.categoryId,
    sellerId: query.sellerId,
    brandIds: query.brandIds,
    minPrice: query.minPrice,
    maxPrice: query.maxPrice,
    minRating: query.minRating,
    inStock: query.inStock,
  };
  for (const [code, values] of Object.entries(query.attrs)) {
    params[ATTR_PREFIX + code] = values;
  }
  for (const [code, range] of Object.entries(query.ranges)) {
    params[`${ATTR_PREFIX}${code}.min`] = range.min;
    params[`${ATTR_PREFIX}${code}.max`] = range.max;
  }
  return params;
}

/** URL keys of every attribute filter in the query, to clear them all at once. */
export function attributeParamKeys(query: ProductQuery): string[] {
  return [
    ...Object.keys(query.attrs).map((code) => ATTR_PREFIX + code),
    ...Object.keys(query.ranges).flatMap((code) => [
      `${ATTR_PREFIX}${code}.min`,
      `${ATTR_PREFIX}${code}.max`,
    ]),
  ];
}

export function attributeParamKey(code: string, bound?: 'min' | 'max'): string {
  return bound ? `${ATTR_PREFIX}${code}.${bound}` : ATTR_PREFIX + code;
}

/** True when any filter (not text, category, sort or page) is set. */
export function hasFilters(query: ProductQuery): boolean {
  return (
    query.brandIds.length > 0 ||
    query.minPrice !== null ||
    query.maxPrice !== null ||
    query.minRating !== null ||
    query.inStock ||
    Object.keys(query.attrs).length > 0 ||
    Object.keys(query.ranges).length > 0
  );
}

// ---------- reviews on the product detail page ----------

/** Review list state, kept in the detail page URL as reviewRating / reviewSort / reviewPage. */
export interface ReviewQuery {
  rating: number | null;
  sort: ReviewSort;
  page: number;
}

export function parseReviewQuery(params: ParamMap): ReviewQuery {
  const rating = intParam(params, 'reviewRating');
  return {
    rating: rating !== null && rating >= 1 && rating <= 5 ? rating : null,
    sort: enumParam(params, 'reviewSort', REVIEW_SORTS, 'newest'),
    page: intParam(params, 'reviewPage') ?? 0,
  };
}
