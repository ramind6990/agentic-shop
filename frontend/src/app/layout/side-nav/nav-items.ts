export interface NavItem {
  label: string;
  path: string;
}

export interface NavGroup {
  label: string;
  items: NavItem[];
}

/**
 * Left navigation, grouped by business area. Each path matches a top-level route; items the user may not open
 * (AREA_PERMISSIONS) are hidden by the side nav.
 */
export const NAV_GROUPS: NavGroup[] = [
  {
    label: 'Catalog',
    items: [
      { label: 'Products', path: '/products' },
      { label: 'Categories', path: '/categories' },
      { label: 'Brands', path: '/brands' },
    ],
  },
  {
    label: 'Sales',
    items: [
      { label: 'Orders', path: '/orders' },
      { label: 'Returns', path: '/returns' },
      { label: 'Refunds', path: '/refunds' },
    ],
  },
  {
    label: 'People',
    items: [
      { label: 'Customers', path: '/customers' },
      { label: 'Sellers', path: '/sellers' },
      { label: 'Staff users', path: '/users' },
    ],
  },
  {
    label: 'Support',
    items: [{ label: 'Tickets', path: '/tickets' }],
  },
  {
    label: 'Risk',
    items: [{ label: 'Account flags', path: '/flags' }],
  },
  {
    label: 'Knowledge',
    items: [{ label: 'Documents', path: '/documents' }],
  },
];
