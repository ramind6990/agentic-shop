import { Permission } from './auth.models';

/**
 * Permission each top-level area needs, keyed by its first URL segment. The single source for the route guard,
 * the side nav and every cross-link ({@link AuthStore.canOpen}), mirroring the backend's endpoint checks.
 */
export const AREA_PERMISSIONS: Record<string, Permission> = {
  products: 'CATALOG_READ',
  categories: 'CATALOG_READ',
  brands: 'CATALOG_READ',
  orders: 'ORDERS_READ',
  returns: 'RETURNS_READ',
  refunds: 'REFUNDS_READ',
  customers: 'CUSTOMERS_READ',
  sellers: 'SELLERS_READ',
  users: 'USERS_READ',
  tickets: 'TICKETS_READ',
  flags: 'FLAGS_READ',
  documents: 'DOCUMENTS_READ',
};

/** The first path segment of router commands or a URL: ['/customers', 5] and '/customers/5' give 'customers'. */
export function firstSegment(target: string | readonly unknown[]): string {
  const path = typeof target === 'string' ? target : String(target[0] ?? '');
  return path.replace(/^\/+/, '').split(/[/?#]/)[0];
}
